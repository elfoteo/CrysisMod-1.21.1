package com.elfoteo.tutorialmod.mixins.layer;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>> {
    @Shadow
    private void renderModel(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, Model model,
            int dyeColor, ResourceLocation textureLocation) {
    }

    @Unique
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/models/armor/nanosuit_overlay.png");

    @Inject(method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;ILnet/minecraft/resources/ResourceLocation;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderModel(PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            Model model,
            int dyeColor,
            ResourceLocation textureLocation,
            CallbackInfo ci) {
        if (RenderState.currentlyRenderingPlayer != null && SuitUtils.isWearingFullNanosuit(RenderState.currentlyRenderingPlayer)) {
            if (RenderState.isRenderingMainPlayer){
                float t = (System.currentTimeMillis() % 5000L) / 5000f; // t in [0.0, 1.0]
                float time = (System.currentTimeMillis() % 100000L) / 1000f; // seconds
                InfraredShader.NANOSUIT_OVERLAY_SHADER.getUniform("Time").set(time);
                float hue = t; // Keep hue in range [0,1]

                // HSL to RGB conversion
                float c = 1.0f;
                float x = 1.0f - Math.abs((hue * 6.0f) % 2.0f - 1.0f);
                float m = 0.0f;

                float r, g, b;

                if (hue < 1.0f / 6.0f) {
                    r = c; g = x; b = 0;
                } else if (hue < 2.0f / 6.0f) {
                    r = x; g = c; b = 0;
                } else if (hue < 3.0f / 6.0f) {
                    r = 0; g = c; b = x;
                } else if (hue < 4.0f / 6.0f) {
                    r = 0; g = x; b = c;
                } else if (hue < 5.0f / 6.0f) {
                    r = x; g = 0; b = c;
                } else {
                    r = c; g = 0; b = x;
                }

                InfraredShader.NANOSUIT_OVERLAY_SHADER.getUniform("ModeModulator").set(r + m, g + m, b + m, 1f);

                VertexConsumer vertexconsumer = buffer.getBuffer(InfraredShader.nanosuitOverlay(TEXTURE));
                model.renderToBuffer(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY, dyeColor);
            }
            ci.cancel(); // Prevent original method from executing
            return;
        }
        if (Nanosuit.currentClientMode == SuitModes.VISOR.get()){
            //VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.armorCutoutNoCull(textureLocation));
            //model.renderToBuffer(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY, dyeColor);
            VertexConsumer vertexconsumer = buffer.getBuffer(InfraredShader.infraredArmorCutoutNoCull(textureLocation));
            model.renderToBuffer(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY, dyeColor);
            ci.cancel();
        }
    }
}
