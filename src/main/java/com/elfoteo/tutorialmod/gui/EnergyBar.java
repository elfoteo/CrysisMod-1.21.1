package com.elfoteo.tutorialmod.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.util.SuitUtils;
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
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class EnergyBar {
    private static final ResourceLocation ENERGY_BAR_BG = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID,
            "textures/gui/energy_bar_bg.png");
    private static final ResourceLocation ENERGY_BAR_FILL = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID,
            "textures/gui/energy_bar_fill.png");
    private static float animatedEnergy = 1.0f;
    private static final float ANIMATION_SPEED = 0.5f;
    private static long lastUpdateTime = System.currentTimeMillis();
    private static final Vector3f COLORMOD = new Vector3f(0.4f, 0.8f, 1f);
    // Add at the top with other fields
    private static boolean redBlinking = false;
    private static long blinkEndTime = 0;
    private static final long BLINK_INTERVAL_MS = 250;
    private static final long TOTAL_BLINK_DURATION_MS = 1000;

    @SubscribeEvent
    public static void onRenderHUD(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH))
            return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !SuitUtils.isWearingFullNanosuit(player))
            return;

        // Compute the current target energy fraction
        float target = Mth.clamp(
                (float) player.getData(ModAttachments.ENERGY) / player.getData(ModAttachments.MAX_ENERGY),
                0f, 1f);

        // Compute deltaTime in seconds
        long now = System.currentTimeMillis();
        float deltaTime = (now - lastUpdateTime) / 1000f; // time in seconds
        lastUpdateTime = now;

        // Animate toward the target energy (current energy value)
        if (Math.abs(animatedEnergy - target) > 0.01f) {
            if (animatedEnergy < target) {
                animatedEnergy = Math.min(animatedEnergy + ANIMATION_SPEED * deltaTime, target);
            } else if (animatedEnergy > target) {
                animatedEnergy = Math.max(animatedEnergy - ANIMATION_SPEED * deltaTime, target);
            }
        } else {
            // If the animation is close enough to the target, keep it there
            animatedEnergy = target;
        }

        // Render the energy bar with the animated value
        renderEnergyBar(event.getGuiGraphics(), animatedEnergy);
    }

    private static void renderEnergyBar(GuiGraphics gui, float frac) {
        int sw = gui.guiWidth(), sh = gui.guiHeight();
        int barWidth = 127, barHeight = 24, pad = 8;
        int bx = sw - barWidth - pad, by = sh - barHeight - pad;
        final int SEGMENT_STEP = 4;

        // ** Snap **: fully on/off 4-px cells
        int fillPx = Math.min(
                Math.round(frac * barWidth / SEGMENT_STEP) * SEGMENT_STEP,
                barWidth);

        // blink color logic unchanged…
        Vector3f color = new Vector3f(COLORMOD);
        if (redBlinking) {
            long now = System.currentTimeMillis();
            if (now > blinkEndTime) {
                redBlinking = false;
            } else if ((now / BLINK_INTERVAL_MS) % 2 == 0) {
                color.set(1f, 0f, 0f); // Red blinking color
            }
        }

        // Draw the background
        renderPNG(gui, ENERGY_BAR_BG, bx, by, barWidth, barHeight, 1f, 1f, color);

        // Draw the filled portion based on the animated energy
        if (fillPx > 0) {
            double scale = Minecraft.getInstance().getWindow().getGuiScale();
            RenderSystem.enableScissor(
                    (int) (bx * scale),
                    (int) ((sh - (by + barHeight)) * scale),
                    (int) (fillPx * scale),
                    (int) (barHeight * scale));
            renderPNG(gui, ENERGY_BAR_FILL, bx, by, fillPx, barHeight, (float) fillPx / barWidth, 1f, color);
            RenderSystem.disableScissor();
        }
    }

    // <<< DO NOT MODIFY THIS METHOD >>>
    private static void renderPNG(GuiGraphics guiGraphics, ResourceLocation texture,
            int x, int y, int width, int height,
            float uScale, float vScale, Vector3f color) {
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().bindForSetup(texture);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        PoseStack pose = guiGraphics.pose();
        Matrix4f mat = pose.last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX);
        RenderSystem.setShaderColor(color.x, color.y, color.z, 1f);
        builder.addVertex(mat, x, y, 0).setUv(0, 0);
        builder.addVertex(mat, x, y + height, 0).setUv(0, vScale);
        builder.addVertex(mat, x + width, y + height, 0).setUv(uScale, vScale);
        builder.addVertex(mat, x + width, y, 0).setUv(uScale, 0);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // Add this method anywhere in the class
    public static void startRedBlink() {
        redBlinking = true;
        blinkEndTime = System.currentTimeMillis() + TOTAL_BLINK_DURATION_MS;
    }

}
