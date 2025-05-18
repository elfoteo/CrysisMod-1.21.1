package com.elfoteo.tutorialmod.mixins.layer;

import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(SpinAttackEffectLayer.class)
public abstract class SpinAttackEffectLayerMixin<T extends LivingEntity> extends RenderLayer<T, PlayerModel<T>> {

    @Shadow @Final public static ResourceLocation TEXTURE;

    @Shadow @Final private ModelPart box;

    public SpinAttackEffectLayerMixin(RenderLayerParent<T, PlayerModel<T>> renderer) {
        super(renderer);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void setupColor(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T livingEntity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (livingEntity.isAutoSpinAttack()) {
            VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

            for(int i = 0; i < 3; ++i) {
                poseStack.pushPose();
                float f = ageInTicks * (float)(-(45 + i * 5));
                poseStack.mulPose(Axis.YP.rotationDegrees(f));
                float f1 = 0.75F * (float)i;
                poseStack.scale(f1, f1, f1);
                poseStack.translate(0.0F, -0.2F + 0.6F * (float)i, 0.0F);
                this.box.render(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
        else if (livingEntity instanceof Player player && player.getData(ModAttachments.SUIT_MODE) == SuitModes.CLOAK.get()){
            VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

            for(int i = 0; i < 3; ++i) {
                poseStack.pushPose();
                float f = ageInTicks * (float)(-(45 + i * 5));
                poseStack.mulPose(Axis.YP.rotationDegrees(f));
                float f1 = 0.75F * (float)i;
                poseStack.scale(f1, f1, f1);
                poseStack.translate(0.0F, -0.2F + 0.6F * (float)i, 0.0F);
                this.box.render(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
    }
}
