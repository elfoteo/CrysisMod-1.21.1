package com.elfoteo.tutorialmod.mixins;

import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.*;
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
public abstract class NanosuitVisorLivingRenderer<T extends LivingEntity, M extends EntityModel<T>>
        implements RenderLayerParent<T, M> {
                                                                                                           // texture
    @Shadow
    protected M model;

    @Shadow
    protected abstract float getAttackAnim(T entity, float partialTicks);

    @Shadow
    protected abstract float getBob(T entity, float partialTicks);

    @Shadow
    protected abstract void setupRotations(T entity, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTicks, float scaleFactor);

    @Shadow
    protected abstract void scale(T entity, PoseStack poseStack, float partialTicks);

    @Shadow
    protected abstract boolean isBodyVisible(T entity);

    @Shadow
    protected abstract RenderType getRenderType(T entity, boolean visible, boolean translucent, boolean glowing);

    @Shadow
    protected abstract float getWhiteOverlayProgress(T entity, float partialTicks);

    @Shadow
    protected List<RenderLayer<T, M>> layers;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
            int packedLight, CallbackInfo cb) {
        if (!((RenderLivingEvent.Pre) NeoForge.EVENT_BUS
                .post(new RenderLivingEvent.Pre(entity, (LivingEntityRenderer) (Object) this, partialTicks, poseStack,
                        buffer, packedLight)))
                .isCanceled()) {
            poseStack.pushPose();
            this.model.attackTime = this.getAttackAnim(entity, partialTicks);
            boolean shouldSit = entity.isPassenger() && entity.getVehicle() != null
                    && entity.getVehicle().shouldRiderSit();
            this.model.riding = shouldSit;
            this.model.young = entity.isBaby();
            float f = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
            float f1 = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
            float f2 = f1 - f;
            if (shouldSit) {
                Entity f8 = entity.getVehicle();
                if (f8 instanceof LivingEntity) {
                    LivingEntity livingentity = (LivingEntity) f8;
                    f = Mth.rotLerp(partialTicks, livingentity.yBodyRotO, livingentity.yBodyRot);
                    f2 = f1 - f;
                    float f7 = Mth.wrapDegrees(f2);
                    if (f7 < -85.0F) {
                        f7 = -85.0F;
                    }

                    if (f7 >= 85.0F) {
                        f7 = 85.0F;
                    }

                    f = f1 - f7;
                    if (f7 * f7 > 2500.0F) {
                        f += f7 * 0.2F;
                    }

                    f2 = f1 - f;
                }
            }

            float f6 = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
            if (LivingEntityRenderer.isEntityUpsideDown(entity)) {
                f6 *= -1.0F;
                f2 *= -1.0F;
            }

            f2 = Mth.wrapDegrees(f2);
            if (entity.hasPose(Pose.SLEEPING)) {
                Direction direction = entity.getBedOrientation();
                if (direction != null) {
                    float f3 = entity.getEyeHeight(Pose.STANDING) - 0.1F;
                    poseStack.translate((float) (-direction.getStepX()) * f3, 0.0F,
                            (float) (-direction.getStepZ()) * f3);
                }
            }

            float f8 = entity.getScale();
            poseStack.scale(f8, f8, f8);
            float f9 = this.getBob(entity, partialTicks);
            this.setupRotations(entity, poseStack, f9, f, partialTicks, f8);
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            this.scale(entity, poseStack, partialTicks);
            poseStack.translate(0.0F, -1.501F, 0.0F);
            float f4 = 0.0F;
            float f5 = 0.0F;
            if (!shouldSit && entity.isAlive()) {
                f4 = entity.walkAnimation.speed(partialTicks);
                f5 = entity.walkAnimation.position(partialTicks);
                if (entity.isBaby()) {
                    f5 *= 3.0F;
                }

                if (f4 > 1.0F) {
                    f4 = 1.0F;
                }
            }

            this.model.prepareMobModel(entity, f5, f4, partialTicks);
            this.model.setupAnim(entity, f5, f4, f9, f2, f6);
            Minecraft minecraft = Minecraft.getInstance();
            boolean flag = this.isBodyVisible(entity);
            boolean flag1 = !flag && !entity.isInvisibleTo(minecraft.player);
            boolean flag2 = minecraft.shouldEntityAppearGlowing(entity);
            boolean flag3 = Nanosuit.currentClientMode == SuitModes.VISOR.get();
            // RenderType rendertype = this.getRenderType(entity, flag, flag1, flag2);
            RenderType rendertype;

            if (flag3){
                rendertype = InfraredShader.INFRARED_RENDER_TYPE;
            }
            else {
                rendertype = this.getRenderType(entity, flag, flag1, flag2);
            }
            if (rendertype != null) {
                VertexConsumer vertexconsumer = buffer.getBuffer(rendertype);
                if (flag3){
                    this.model.renderToBuffer(poseStack, vertexconsumer, 0xF000F, 0xFFFFFFFF, 0xFF0000FF);
                }
                else {
                    int i = LivingEntityRenderer.getOverlayCoords(entity,
                            this.getWhiteOverlayProgress(entity, partialTicks));
                    this.model.renderToBuffer(poseStack, vertexconsumer, packedLight, i, flag1 ?
                    654311423 : -1);
                }
            }

            if (!entity.isSpectator()) {
                for (RenderLayer<T, M> renderlayer : this.layers) {
                    renderlayer.render(poseStack, buffer, packedLight, entity, f5, f4, partialTicks, f9, f2, f6);
                }
            }

            poseStack.popPose();
            //((EntityRenderer<T>) (LivingEntityRenderer<T, M>) (Object) this).render(entity, entityYaw, partialTicks,
            //        poseStack, buffer, packedLight);

            NeoForge.EVENT_BUS
                    .post(new RenderLivingEvent.Post(entity, (LivingEntityRenderer) (Object) this, partialTicks,
                            poseStack, buffer, packedLight));
        }
        cb.cancel();
    }
}
