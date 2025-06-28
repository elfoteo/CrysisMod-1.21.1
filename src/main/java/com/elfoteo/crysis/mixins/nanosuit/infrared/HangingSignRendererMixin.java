package com.elfoteo.crysis.mixins.nanosuit.infrared;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
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

/**
 * Mixin to render hanging signs in infrared when the player suit is in VISOR mode.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(HangingSignRenderer.class)
public abstract class HangingSignRendererMixin extends SignRenderer {

    public HangingSignRendererMixin(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Shadow @Final private java.util.Map<WoodType, HangingSignRenderer.HangingSignModel> hangingSignModels;

    @Shadow
    abstract void translateSign(PoseStack poseStack, float yRot, BlockState state);

    @Shadow
    abstract void renderSignModel(
            PoseStack poseStack,
            int packedLight,
            int packedOverlay,
            Model model,
            VertexConsumer vertexConsumer
    );

    @Shadow
    abstract Material getSignMaterial(WoodType woodType);

    @Inject(
        method = "render(Lnet/minecraft/world/level/block/entity/SignBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRender(
        SignBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay,
        CallbackInfo ci
    ) {
        // Only override when the suit is in VISOR (infrared) mode
        if (Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) != SuitModes.VISOR.get()) {
            return;
        }

        BlockState state = blockEntity.getBlockState();
        SignBlock signBlock = (SignBlock) state.getBlock();
        WoodType woodType = SignBlock.getWoodType(signBlock);
        HangingSignRenderer.HangingSignModel model = this.hangingSignModels.get(woodType);

        // Decide which parts are visible (plank vs. chains)
        model.evaluateVisibleParts(state);

        // 1) Push and transform just like vanilla
        poseStack.pushPose();
        poseStack.translate((double)0.5F, (double)0.9375F, (double)0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-signBlock.getYRotationDegrees(state)));
        poseStack.translate(0.0F, -0.3125F, 0.0F);

        // 2) Fetch vanilla wood material & its texture location
        Material material = this.getSignMaterial(woodType);
        ResourceLocation tex = material.texture();

        // 3) Get our infrared RenderType
        RenderType infraredRT = InfraredShader.infraredBlock(tex);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        // 4) Render only the model (board and chains) with infrared
        VertexConsumer infraredVC = bufferSource.getBuffer(infraredRT);
        this.renderSignModel(poseStack, packedLight, packedOverlay, model, infraredVC);

        poseStack.popPose();
        ci.cancel(); // Skip the vanilla hanging‐sign render entirely
    }
}
