package com.elfoteo.tutorialmod.gui;

import com.elfoteo.tutorialmod.event.ClientPowerJumpEvents;
import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
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
    private static final ResourceLocation BASE_TEXTURE        = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/gui/nanosuit_overlay_base.png");
    private static final ResourceLocation ENERGY_FILL_TEXTURE = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/gui/energy_bar_fill.png");
    private static final int BASE_WIDTH  = 127, BASE_HEIGHT = 28, PADDING = 8;
    private static final Rect JUMP_BAR = new Rect(12, 24, 110, 2);

    // energy anim state
    private static float animatedEnergy = 1f;
    // jump anim state (smooth visual)
    private static float animatedJump   = 0f;

    private static final float ENERGY_ANIM_SPEED = 0.85f; // TODO: This is not in seconds, refactor
    private static final float JUMP_ANIM_SPEED   = 3f; // higher = snappier
    private static long  lastTime = System.currentTimeMillis();

    private static boolean redBlinking = false;
    private static long    blinkEndTime = 0;
    private static final long BLINK_INTERVAL_MS       = 250;
    private static final long TOTAL_BLINK_DURATION_MS = 1000;

    @SubscribeEvent
    public static void onRenderHUD(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.PLAYER_HEALTH.equals(event.getName())) return;
        Player player = Minecraft.getInstance().player;
        if (player == null || !SuitUtils.isWearingFullNanosuit(player)) return;

        GuiGraphics gui = event.getGuiGraphics();
        int sw = gui.guiWidth(), sh = gui.guiHeight();
        int baseX = sw - BASE_WIDTH - PADDING;
        int baseY = sh - BASE_HEIGHT - PADDING;

        // delta time
        long now = System.currentTimeMillis();
        float dt = (now - lastTime) / 1000f;
        lastTime = now;

        // --- Energy interpolation ---
        float targetEnergy = Mth.clamp(player.getData(ModAttachments.ENERGY) / (float)player.getData(ModAttachments.MAX_ENERGY), 0f, 1f);
        if (Math.abs(animatedEnergy - targetEnergy) > 0.001f) {
            animatedEnergy += Mth.clamp(targetEnergy - animatedEnergy, -ENERGY_ANIM_SPEED * dt, ENERGY_ANIM_SPEED * dt);
        } else animatedEnergy = targetEnergy;

        // --- Jump interpolation (smooth) ---
        float targetJump = Mth.clamp(ClientPowerJumpEvents.getCurrentJumpCharge(player), 0f, 1f);
        // simple exponential approach: d = (target - current) * JUMP_ANIM_SPEED * dt
        animatedJump += (targetJump - animatedJump) * JUMP_ANIM_SPEED * dt;

        // draw both
        drawEnergyBar(gui, baseX, baseY, animatedEnergy);
        drawJumpBar  (gui, baseX, baseY, animatedJump);
    }

    private static void drawEnergyBar(GuiGraphics gui, int baseX, int baseY, float fraction) {
        // blink logic
        float R = 0.4f, G = 0.8f, B = 1f;
        if (redBlinking && System.currentTimeMillis() < blinkEndTime) {
            if ((System.currentTimeMillis() / BLINK_INTERVAL_MS) % 2 == 0) {
                R = 1f; G = 0f; B = 0f;
            }
        } else redBlinking = false;

        // base overlay
        drawTexturedRect(gui, BASE_TEXTURE, baseX, baseY, BASE_WIDTH, BASE_HEIGHT, 1f, 1f, R, G, B);

        // fill
        int ENERGY_BAR_FILL_HEIGHT = 24;
        int ENERGY_BAR_FILL_WIDTH = 127;
        int fillPx = Math.min(Math.round(fraction * ENERGY_BAR_FILL_WIDTH / 4f) * 4, ENERGY_BAR_FILL_WIDTH);
        if (fillPx > 0) {
            double scale = Minecraft.getInstance().getWindow().getGuiScale();
            RenderSystem.enableScissor(
                    (int)(baseX * scale),
                    (int)((gui.guiHeight() - (baseY + ENERGY_BAR_FILL_HEIGHT)) * scale),
                    (int)(fillPx * scale),
                    (int)(ENERGY_BAR_FILL_HEIGHT * scale)
            );
            drawTexturedRect(gui, ENERGY_FILL_TEXTURE, baseX, baseY, fillPx, ENERGY_BAR_FILL_HEIGHT, (float)fillPx / ENERGY_BAR_FILL_WIDTH, 1f, R, G, B);
            RenderSystem.disableScissor();
        }
    }

    private static void drawJumpBar(GuiGraphics gui, int baseX, int baseY, float fraction) {
        int x = baseX + JUMP_BAR.x, y = baseY + JUMP_BAR.y;
        int w = JUMP_BAR.width,   h = JUMP_BAR.height;
        int fill = (int)(w * fraction);

        gui.fill(x, y, x + w, y + h, 0xFF202020);      // background
        if (fill > 0) gui.fill(x, y, x + fill, y + h, 0xFFFFFF00); // yellow fill
    }

    private static void drawTexturedRect(GuiGraphics gui, ResourceLocation tex,
                                         int x, int y, int w, int h, float uS, float vS, float r, float g, float b) {
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().bindForSetup(tex);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        Matrix4f mat = gui.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        RenderSystem.setShaderColor(r, g, b, 1f);
        bb.addVertex(mat, x,     y,     0).setUv(0, 0);
        bb.addVertex(mat, x,     y + h, 0).setUv(0, vS);
        bb.addVertex(mat, x + w, y + h, 0).setUv(uS, vS);
        bb.addVertex(mat, x + w, y,     0).setUv(uS, 0);
        BufferUploader.drawWithShader(bb.build());
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f,1f,1f,1f);
    }

    public static void startRedBlink() {
        redBlinking   = true;
        blinkEndTime = System.currentTimeMillis() + TOTAL_BLINK_DURATION_MS;
    }

    private static final class Rect {
        final int x, y, width, height;
        Rect(int x,int y,int w,int h){ this.x=x; this.y=y; width=w; height=h; }
    }
}
