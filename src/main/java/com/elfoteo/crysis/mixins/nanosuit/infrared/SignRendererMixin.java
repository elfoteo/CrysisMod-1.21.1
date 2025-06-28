package com.elfoteo.crysis.mixins.nanosuit.infrared;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Objects;

@Mixin(SignRenderer.class)
@OnlyIn(Dist.CLIENT)
public abstract class SignRendererMixin implements BlockEntityRenderer<SignBlockEntity> {
    @Shadow @Final private Map<WoodType, SignRenderer.SignModel> signModels;
    @Shadow @Final private Font font;
    @Shadow
    abstract Material getSignMaterial(WoodType woodType);
    @Shadow
    abstract void translateSign(PoseStack poseStack, float yRot, BlockState state);

    @Shadow public abstract float getSignModelRenderScale();

    /**
     * Overrides the vanilla sign rendering to use infrared shader when in VISOR mode.
     */
    @Inject(
            method = "render(Lnet/minecraft/world/level/block/entity/SignBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRender(
            SignBlockEntity signEntity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            CallbackInfo ci
    ) {
        // Only apply in VISOR (infrared) mode
        if (Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) != SuitModes.VISOR.get()) {
            return;
        }

        BlockState state = signEntity.getBlockState();
        SignBlock signBlock = (SignBlock) state.getBlock();
        WoodType woodType = SignBlock.getWoodType(signBlock);
        SignRenderer.SignModel model = this.signModels.get(woodType);

        // Prepare vanilla transforms
        poseStack.pushPose();
        // Match vanilla sign translation/rotation
        this.translateSign(poseStack, -signBlock.getYRotationDegrees(state), state);

        // Obtain the vanilla sign texture ResourceLocation
        Material mat = this.getSignMaterial(woodType);
        ResourceLocation texRL = mat.texture();

        // Create infrared RenderType
        RenderType infraredRT = InfraredShader.infraredBlock(texRL);
        VertexConsumer vc = bufferSource.getBuffer(infraredRT);

        float f = this.getSignModelRenderScale();
        poseStack.scale(f, -f, -f);
        Objects.requireNonNull(model);
        SignRenderer.SignModel signrenderer$signmodel = model;
        signrenderer$signmodel.root.render(poseStack, vc, packedLight, packedOverlay);

        poseStack.popPose();
        // Skip vanilla renderer
        ci.cancel();
    }
}
