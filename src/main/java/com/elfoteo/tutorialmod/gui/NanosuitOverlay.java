package com.elfoteo.tutorialmod.gui;

import com.elfoteo.tutorialmod.event.ClientPowerJumpEvents;
import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.elfoteo.tutorialmod.util.SuitUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class NanosuitOverlay {
    private static final ResourceLocation BASE_TEXTURE            = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/gui/nanosuit_overlay_base.png");
    private static final ResourceLocation ENERGY_FILL_TEXTURE     = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/gui/energy_bar_fill.png");
    private static final ResourceLocation ARMOR_MODE_FILL_TEXTURE = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/gui/armor_mode_fill.png");
    private static final ResourceLocation CLOAK_MODE_TEXTURE      = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/gui/cloak_mode_fill.png");
    private static final ResourceLocation VISOR_MODE_TEXTURE      = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/gui/visor_mode_fill.png");
    private static final ResourceLocation VIGNETTE_TEXTURE        = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/gui/vignette.png");

    private static final int BASE_WIDTH   = 127, BASE_HEIGHT = 36, PADDING = 8;
    private static final Rect JUMP_BAR               = new Rect(12, 24, 113, 2);
    private static final Rect ARMOR_MODE_FILL_RECT   = new Rect(50, 28, 39, 6);
    private static final Rect CLOAK_MODE_FILL_RECT   = new Rect(15, 28, 37, 6);
    private static final Rect VISOR_MODE_FILL_RECT   = new Rect(87, 28, 37, 6);

    private static float animatedEnergy = 1f;
    // How quickly (in units of “fraction per second”) the bar moves toward the true value:
    private static final float JUMP_LERP_SPEED = 2f;

    // We keep a persistent “displayed” jump‐charge that gradually moves toward the real one
    private static float displayedJump = 0f;
    private static long  lastTime = System.currentTimeMillis();

    private static boolean redBlinking = false;
    private static long blinkEndTime = 0;
    private static final long BLINK_INTERVAL_MS = 250;
    private static final long TOTAL_BLINK_DURATION_MS = 1000;

    private static float vignetteAlpha = 0f;
    private static boolean wasInCloakMode = false;
    private static final float VIGNETTE_FADE_IN_TIME = .3f;

    @SubscribeEvent
    public static void onRenderHUD(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.PLAYER_HEALTH.equals(event.getName())) return;
        Player player = Minecraft.getInstance().player;
        if (player == null || !SuitUtils.isWearingFullNanosuit(player)) return;

        GuiGraphics gui = event.getGuiGraphics();
        int sw = gui.guiWidth(), sh = gui.guiHeight();
        int baseX = sw - BASE_WIDTH - PADDING;
        int baseY = sh - BASE_HEIGHT - PADDING;

        long now = System.currentTimeMillis();
        float dt = (now - lastTime) / 1000f;
        lastTime = now;

        // ——— Energy interpolation is unchanged ———
        float targetEnergy = Mth.clamp(
                player.getData(ModAttachments.ENERGY) / (float) player.getData(ModAttachments.MAX_ENERGY),
                0f, 1f
        );
        if (Math.abs(animatedEnergy - targetEnergy) > 0.001f) {
            animatedEnergy += Mth.clamp(targetEnergy - animatedEnergy, -0.85f * dt, 0.85f * dt);
        } else {
            animatedEnergy = targetEnergy;
        }

        // ——— Compute the “true” jump charge instantly ———
        float trueJump = Mth.clamp(
                ClientPowerJumpEvents.getCurrentJumpCharge(player),
                0f, 1f
        );

        // ——— Linear (constant‐rate) interpolation toward trueJump ———
        float diff = trueJump - displayedJump;
        float maxDelta = JUMP_LERP_SPEED * dt;
        if (Math.abs(diff) <= maxDelta) {
            // Close enough: snap to the real value
            displayedJump = trueJump;
        } else {
            // Move up or down by maxDelta
            displayedJump += Math.signum(diff) * maxDelta;
        }

        boolean inCloak = Nanosuit.currentClientMode == SuitModes.CLOAK.get();
        if (inCloak && !wasInCloakMode) {
            vignetteAlpha = 0f;
        }
        wasInCloakMode = inCloak;

        if (inCloak) {
            vignetteAlpha = Mth.clamp(
                    vignetteAlpha + (dt / VIGNETTE_FADE_IN_TIME) * 0.5f,
                    0f, 1f
            );
        } else {
            vignetteAlpha = 0f;
        }

        if (vignetteAlpha > 0f) {
            renderCloakVignette(gui, sw, sh, vignetteAlpha);
        }

        drawEnergyBar(gui, baseX, baseY, animatedEnergy);
        drawJumpBar(gui, baseX, baseY, displayedJump);
        drawModeFill(gui, baseX, baseY);
    }

    private static void renderCloakVignette(GuiGraphics guiGraphics, int width, int height, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().bindForSetup(NanosuitOverlay.VIGNETTE_TEXTURE);
        RenderSystem.setShaderTexture(0, NanosuitOverlay.VIGNETTE_TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();

        PoseStack poseStack = guiGraphics.pose();
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        builder.addVertex(matrix, 0, 0, 0).setUv(0, 0);
        builder.addVertex(matrix, 0, height, 0).setUv(0, 1f);
        builder.addVertex(matrix, width, height, 0).setUv(1f, 1f);
        builder.addVertex(matrix, width, 0, 0).setUv(1f, 0);

        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();

    }

    private static void drawEnergyBar(GuiGraphics gui, int baseX, int baseY, float fraction) {
        float R = 0.4f, G = 0.8f, B = 1f;
        if (redBlinking && System.currentTimeMillis() < blinkEndTime) {
            if ((System.currentTimeMillis() / BLINK_INTERVAL_MS) % 2 == 0) {
                R = 1f; G = 0f; B = 0f;
            }
        } else {
            redBlinking = false;
        }

        drawTexturedRect(gui, BASE_TEXTURE, baseX, baseY, BASE_WIDTH, BASE_HEIGHT, 1f, 1f, R, G, B);

        int ENERGY_BAR_FILL_HEIGHT = 24;
        int ENERGY_BAR_FILL_WIDTH  = 127;
        int fillPx = Math.min(Math.round(fraction * ENERGY_BAR_FILL_WIDTH / 4f) * 4, ENERGY_BAR_FILL_WIDTH);
        if (fillPx > 0) {
            double scale = Minecraft.getInstance().getWindow().getGuiScale();
            RenderSystem.enableScissor(
                    (int) (baseX * scale),
                    (int) ((gui.guiHeight() - (baseY + ENERGY_BAR_FILL_HEIGHT)) * scale),
                    (int) (fillPx * scale),
                    (int) (ENERGY_BAR_FILL_HEIGHT * scale)
            );
            drawTexturedRect(gui, ENERGY_FILL_TEXTURE, baseX, baseY, fillPx, ENERGY_BAR_FILL_HEIGHT, (float) fillPx / ENERGY_BAR_FILL_WIDTH, 1f, R, G, B);
            RenderSystem.disableScissor();
        }
    }

    private static void drawJumpBar(GuiGraphics gui, int baseX, int baseY, float fraction) {
        int x = baseX + JUMP_BAR.x,
                y = baseY + JUMP_BAR.y;
        int w = JUMP_BAR.width,
                h = JUMP_BAR.height;
        // Use Math.round so that at exactly 1f we fill all pixels
        int fill = Math.round(w * fraction);

        // draw the dark background
        gui.fill(x, y, x + w, y + h, 0xFF202020);
        if (fill > 0) {
            // draw the yellow fill
            gui.fill(x, y, x + fill, y + h, 0xFFFFFF00);
        }
    }

    private static void drawModeFill(GuiGraphics gui, int baseX, int baseY) {
        int currentMode = Nanosuit.currentClientMode;
        if (currentMode == SuitModes.ARMOR.get()) {
            Rect r = ARMOR_MODE_FILL_RECT;
            drawTexturedRect(gui, ARMOR_MODE_FILL_TEXTURE, baseX + r.x, baseY + r.y, r.width, r.height, 1f, 1f, 1f, 1f, 1f);
        } else if (currentMode == SuitModes.CLOAK.get()) {
            Rect r = CLOAK_MODE_FILL_RECT;
            drawTexturedRect(gui, CLOAK_MODE_TEXTURE, baseX + r.x, baseY + r.y, r.width, r.height, 1f, 1f, 1f, 1f, 1f);
        } else if (currentMode == SuitModes.VISOR.get()) {
            Rect r = VISOR_MODE_FILL_RECT;
            drawTexturedRect(gui, VISOR_MODE_TEXTURE, baseX + r.x, baseY + r.y, r.width, r.height, 1f, 1f, 1f, 1f, 1f);
        }
    }

    private static void drawTexturedRect(GuiGraphics gui, ResourceLocation tex,
                                         int x, int y, int w, int h, float uS, float vS,
                                         float r, float g, float b) {
        drawTexturedRect(gui, tex, x, y, w, h, uS, vS, r, g, b, 1f);
    }

    private static void drawTexturedRect(GuiGraphics gui, ResourceLocation tex,
                                         int x, int y, int w, int h, float uS, float vS,
                                         float r, float g, float b, float a) {
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().bindForSetup(tex);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(r, g, b, a);
        Matrix4f mat = gui.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.addVertex(mat, x,     y,     0).setUv(0, 0);
        bb.addVertex(mat, x,     y + h, 0).setUv(0, vS);
        bb.addVertex(mat, x + w, y + h, 0).setUv(uS, vS);
        bb.addVertex(mat, x + w, y,     0).setUv(uS, 0);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void startRedBlink() {
        redBlinking = true;
        blinkEndTime = System.currentTimeMillis() + TOTAL_BLINK_DURATION_MS;
    }

    private static final class Rect {
        final int x, y, width, height;
        Rect(int x, int y, int w, int h) {
            this.x = x; this.y = y; width = w; height = h;
        }
    }
}
