package com.elfoteo.tutorialmod.mixins.layer;

import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>> {
    @Shadow
    private void renderModel(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, Model model,
            int dyeColor, ResourceLocation textureLocation) {
    }

    @Inject(method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;ILnet/minecraft/resources/ResourceLocation;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderModel(PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            Model model,
            int dyeColor,
            ResourceLocation textureLocation,
            CallbackInfo ci) {
        if (RenderState.currentlyRenderingPlayer != null && SuitUtils.isWearingFullNanosuit(RenderState.currentlyRenderingPlayer)) {
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
