package com.elfoteo.crysis.mixins.nanosuit.infrared;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShulkerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(ShulkerBoxRenderer.class)
public abstract class ShulkerBoxRendererMixin implements BlockEntityRenderer<ShulkerBoxBlockEntity> {
    @Shadow @Final private ShulkerModel<?> model;

    @Inject(
            method = "render(Lnet/minecraft/world/level/block/entity/ShulkerBoxBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void renderInfrared(
            ShulkerBoxBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            CallbackInfo ci
    ) {
        if (Minecraft.getInstance().player == null
                || !Minecraft.getInstance()
                .player
                .getData(ModAttachments.SUIT_MODE)
                .equals(SuitModes.VISOR.get())) {
            return;
        }

        // cancel the vanilla render
        ci.cancel();

        // determine facing
        Direction facing = Direction.UP;
        if (blockEntity.hasLevel()) {
            BlockState state = blockEntity.getLevel().getBlockState(blockEntity.getBlockPos());
            if (state.getBlock() instanceof ShulkerBoxBlock) {
                facing = state.getValue(ShulkerBoxBlock.FACING);
            }
        }

        // pick the correct shulker texture
        DyeColor dye = blockEntity.getColor();
        Material material = (dye == null
                ? Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION
                : Sheets.SHULKER_TEXTURE_LOCATION.get(dye.getId())
        );

        // extract ResourceLocation and build infrared RenderType
        ResourceLocation texRL = material.texture();
        RenderType infraredRT = InfraredShader.infraredBlock(texRL);
        VertexConsumer vc = bufferSource.getBuffer(infraredRT);

        // push transforms
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(0.9995F, 0.9995F, 0.9995F);
        poseStack.mulPose(facing.getRotation());
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.translate(0.0F, -1.0F, 0.0F);

        // animate lid
        ModelPart lid = this.model.getLid();
        float prog = blockEntity.getProgress(partialTick);
        lid.setPos(0.0F, 24.0F - prog * 8.0F, 0.0F);
        lid.yRot = (float)Math.toRadians(270) * prog;

        // render with infrared shader
        this.model.renderToBuffer(poseStack, vc, packedLight, packedOverlay);

        poseStack.popPose();
        ci.cancel();
    }
}
