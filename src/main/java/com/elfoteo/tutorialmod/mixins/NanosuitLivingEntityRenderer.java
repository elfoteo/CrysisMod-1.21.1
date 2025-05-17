package com.elfoteo.tutorialmod.mixins;

import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.RenderState;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.elfoteo.tutorialmod.util.SuitUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class NanosuitLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> implements RenderLayerParent<T, M> {

    @Shadow protected M model;
    @Shadow protected abstract float getAttackAnim(T entity, float partialTicks);
    @Shadow protected abstract float getBob(T entity, float partialTicks);
    @Shadow protected abstract void setupRotations(T entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, float scaleFactor);
    @Shadow protected abstract void scale(T entity, PoseStack poseStack, float partialTicks);
    @Shadow protected abstract boolean isBodyVisible(T entity);
    @Shadow protected abstract RenderType getRenderType(T entity, boolean visible, boolean translucent, boolean glowing);
    @Shadow protected abstract float getWhiteOverlayProgress(T entity, float partialTicks);
    @Shadow protected List<RenderLayer<T, M>> layers;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void renderWithoutPlayerModel(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                         MultiBufferSource buffer, int packedLight, CallbackInfo ci) {

        if (Nanosuit.currentClientMode != SuitModes.VISOR.get()) return;
        if (!(entity instanceof Player player)) return;

        if (!SuitUtils.isWearingFullNanosuit(player)
                || player.getData(ModAttachments.SUIT_MODE) != SuitModes.CLOAK.get()) {
            return; // not cloaked, allow normal render
        }

        // Cancel vanilla render, but replicate it minus model render
        if (!((RenderLivingEvent.Pre)NeoForge.EVENT_BUS.post(
                new RenderLivingEvent.Pre(entity, (LivingEntityRenderer<?, ?>)(Object)this,
                        partialTicks, poseStack, buffer, packedLight))).isCanceled()) {

            poseStack.pushPose();
            this.model.attackTime = this.getAttackAnim(entity, partialTicks);
            boolean shouldSit = entity.isPassenger() && entity.getVehicle() != null && entity.getVehicle().shouldRiderSit();
            this.model.riding = shouldSit;
            this.model.young = entity.isBaby();

            float bodyRot = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
            float headRot = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
            float rotDiff = headRot - bodyRot;

            if (shouldSit) {
                Entity vehicle = entity.getVehicle();
                if (vehicle instanceof LivingEntity livingVehicle) {
                    bodyRot = Mth.rotLerp(partialTicks, livingVehicle.yBodyRotO, livingVehicle.yBodyRot);
                    rotDiff = headRot - bodyRot;
                    float clamped = Mth.wrapDegrees(rotDiff);
                    clamped = Mth.clamp(clamped, -85.0F, 85.0F);
                    bodyRot = headRot - clamped;
                    if (clamped * clamped > 2500.0F) {
                        bodyRot += clamped * 0.2F;
                    }
                    rotDiff = headRot - bodyRot;
                }
            }

            float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
            if (LivingEntityRenderer.isEntityUpsideDown(entity)) {
                pitch *= -1.0F;
                rotDiff *= -1.0F;
            }

            rotDiff = Mth.wrapDegrees(rotDiff);

            if (entity.hasPose(Pose.SLEEPING)) {
                Direction direction = entity.getBedOrientation();
                if (direction != null) {
                    float eyeOffset = entity.getEyeHeight(Pose.STANDING) - 0.1F;
                    poseStack.translate(-direction.getStepX() * eyeOffset, 0.0F, -direction.getStepZ() * eyeOffset);
                }
            }

            float scale = entity.getScale();
            poseStack.scale(scale, scale, scale);

            float bob = this.getBob(entity, partialTicks);
            this.setupRotations(entity, poseStack, bob, bodyRot, partialTicks, scale);
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            this.scale(entity, poseStack, partialTicks);
            poseStack.translate(0.0F, -1.501F, 0.0F);

            float limbSwingAmount = 0.0F;
            float limbSwing = 0.0F;

            if (!shouldSit && entity.isAlive()) {
                limbSwingAmount = entity.walkAnimation.speed(partialTicks);
                limbSwing = entity.walkAnimation.position(partialTicks);
                if (entity.isBaby()) {
                    limbSwing *= 3.0F;
                }
                limbSwingAmount = Math.min(1.0F, limbSwingAmount);
            }

            this.model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
            this.model.setupAnim(entity, limbSwing, limbSwingAmount, bob, rotDiff, pitch);

            // ? SKIP: Rendering the base model (we're invisible)
            // ? KEEP: Rendering layers
            if (!entity.isSpectator()) {
                RenderState.isRenderingtranslucent = true;
                for (RenderLayer<T, M> renderlayer : this.layers) {
                    renderlayer.render(poseStack, buffer, packedLight, entity,
                            limbSwing, limbSwingAmount, partialTicks, bob, rotDiff, pitch);
                }
                RenderState.isRenderingtranslucent = false;
            }

            poseStack.popPose();

            NeoForge.EVENT_BUS.post(new RenderLivingEvent.Post(entity, (LivingEntityRenderer<?, ?>) (Object) this,
                    partialTicks, poseStack, buffer, packedLight) {
            });

            ci.cancel(); // prevent vanilla method
        }
    }
}
