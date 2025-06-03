package com.elfoteo.crysis.event;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.flag.CaptureTheFlagData;
import com.elfoteo.crysis.gui.NanosuitSkillTree;
import com.elfoteo.crysis.gui.NanosuitVisorInsights;
import com.elfoteo.crysis.gui.util.EntityDisposition;
import com.elfoteo.crysis.keybindings.ModKeyBindings;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SuitUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = CrysisMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ModClientEvents {

    private static final ResourceLocation HOSTILE_ICON = ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "textures/gui/markers/hostile.png");
    private static final ResourceLocation NEUTRAL_ICON = ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "textures/gui/markers/neutral.png");
    private static final ResourceLocation PACIFIC_ICON = ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "textures/gui/markers/pacific.png");

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (ModKeyBindings.SKILLTREE_KEY != null && ModKeyBindings.SKILLTREE_KEY.consumeClick() && player != null && SuitUtils.isWearingFullNanosuit(player)) {
            if (mc.screen == null) {
                mc.setScreen(new NanosuitSkillTree());
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!NanosuitVisorInsights.MARKED_ENTITIES.containsKey(entity.getUUID())) return;

        PoseStack poseStack = event.getPoseStack();
        double yOffset = entity.getBbHeight() + Math.min(entity.getBbHeight()*0.2, 0.5f);

        // Draw floating icon with constant on-screen size (no perspective scaling)
        poseStack.pushPose();
        poseStack.translate(0, yOffset, 0);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

        // Compute distance from camera to entity to counteract perspective shrink
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        double dx = entity.getX() - camPos.x;
        double dy = entity.getY() - camPos.y;
        double dz = entity.getZ() - camPos.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Choose a base scale factor (adjust 0.025f as needed for desired on-screen size)
        float baseScale = 0.03f;
        float dynamicScale = baseScale * Math.max(distance, 10);
        poseStack.scale(dynamicScale, dynamicScale, dynamicScale);

        ResourceLocation icon;
        EntityDisposition.Disposition disposition = EntityDisposition.get(entity);

        switch (disposition) {
            case PROJECTILE, BOSS, HOSTILE, UNKNOWN -> icon = HOSTILE_ICON;
            case PLAYER -> icon = NEUTRAL_ICON;
            default -> icon = PACIFIC_ICON;
        }

        drawTargetIndicator(
                poseStack,
                event.getMultiBufferSource(),
                icon,
                -0.5f, -0.5f, 1.0f, 1.0f, // x, y, width, height in world space
                0.0f, 1.0f, // uS, vS (texture coordinates)
                1.0f, 1.0f, 1.0f, 1.0f // r, g, b, a
        );

        poseStack.popPose();
    }

    private static void drawTargetIndicator(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ResourceLocation tex,
            float x, float y, float w, float h,
            float uS, float vS,
            float r, float g, float b, float a
    ) {
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().bindForSetup(tex);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(r, g, b, a);

        Matrix4f mat = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(InfraredShader.targetIndicator(tex));
        float u0 = 0.0f;
        float v0 = 1.0f;
        float u1 = 1.0f;
        float v1 = 0.0f;

        // Define the quad vertices (counter-clockwise order)
        buffer.addVertex(mat, x,     y,     0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, -1);
        buffer.addVertex(mat, x,     y + h, 0).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, -1);
        buffer.addVertex(mat, x + w, y + h, 0).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, -1);
        buffer.addVertex(mat, x + w, y,     0).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, -1);

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
