package com.elfoteo.crysis.mixins.nanosuit;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillState;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class NanosuitLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> implements RenderLayerParent<T, M> {

    @Shadow protected M model;
    @Shadow protected abstract float getAttackAnim(T entity, float partialTicks);
    @Shadow protected abstract float getBob(T entity, float partialTicks);
    @Shadow protected abstract void setupRotations(T entity, PoseStack poseStack, float ageInTicks,
                                                   float rotationYaw, float partialTicks, float scaleFactor);
    @Shadow protected abstract void scale(T entity, PoseStack poseStack, float partialTicks);
    @Final @Shadow protected List<RenderLayer<T, M>> layers;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        boolean isCloak = false;
        boolean isVisor = Nanosuit.currentClientMode == SuitModes.VISOR.get();

        if (entity instanceof AbstractClientPlayer player) {
            isCloak = player.getData(ModAttachments.SUIT_MODE) == SuitModes.CLOAK.get();
            // Cloak mode: hide all other players
            if (isCloak && entity != mc.player) {
                ci.cancel();
                return;
            }
        }
        // Only intercept in cloak or visor modes
        if (!isCloak && !isVisor) {
            return;
        }

        // Trigger pre-render event
        if (!((RenderLivingEvent.Pre) NeoForge.EVENT_BUS.post(
                new RenderLivingEvent.Pre(entity, (LivingEntityRenderer<?, ?>) (Object) this,
                        partialTicks, poseStack, buffer, packedLight))).isCanceled()) {

            poseStack.pushPose();
            // Basic model setup
            model.attackTime = this.getAttackAnim(entity, partialTicks);
            boolean shouldSit = entity.isPassenger() && entity.getVehicle() != null
                    && entity.getVehicle().shouldRiderSit();
            model.riding = shouldSit;
            model.young = entity.isBaby();

            // Rotation interpolation
            float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
            float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
            float yawDelta = headYaw - bodyYaw;
            if (shouldSit) {
                Entity vehicle = entity.getVehicle();
                if (vehicle instanceof LivingEntity strider) {
                    bodyYaw = Mth.rotLerp(partialTicks, strider.yBodyRotO, strider.yBodyRot);
                    yawDelta = headYaw - bodyYaw;
                    float wrapped = Mth.wrapDegrees(yawDelta);
                    wrapped = Mth.clamp(wrapped, -85.0F, 85.0F);
                    bodyYaw = headYaw - wrapped;
                    if (wrapped * wrapped > 2500.0F) {
                        bodyYaw += wrapped * 0.2F;
                    }
                    yawDelta = headYaw - bodyYaw;
                }
            }

            float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
            if (LivingEntityRenderer.isEntityUpsideDown(entity)) {
                pitch = -pitch;
                yawDelta = -yawDelta;
            }
            yawDelta = Mth.wrapDegrees(yawDelta);

            if (entity.hasPose(Pose.SLEEPING)) {
                Direction dir = entity.getBedOrientation();
                if (dir != null) {
                    float offset = entity.getEyeHeight(Pose.STANDING) - 0.1F;
                    poseStack.translate(-dir.getStepX() * offset, 0.0F, -dir.getStepZ() * offset);
                }
            }

            float scaleVal = entity.getScale();
            poseStack.scale(scaleVal, scaleVal, scaleVal);
            float bob = this.getBob(entity, partialTicks);
            this.setupRotations(entity, poseStack, bob, bodyYaw, partialTicks, scaleVal);
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            this.scale(entity, poseStack, partialTicks);
            poseStack.translate(0.0F, -1.501F, 0.0F);

            // Animation
            float walkSpeed = 0.0F;
            float walkPos = 0.0F;
            if (!shouldSit && entity.isAlive()) {
                walkSpeed = entity.walkAnimation.speed(partialTicks);
                walkPos = entity.walkAnimation.position(partialTicks);
                if (entity.isBaby()) {
                    walkPos *= 3.0F;
                }
                walkSpeed = Math.min(walkSpeed, 1.0F);
            }
            model.prepareMobModel(entity, walkPos, walkSpeed, partialTicks);
            model.setupAnim(entity, walkPos, walkSpeed, bob, yawDelta, pitch);

            // Determine if the entity is a player with Thermal Dampeners skill unlocked
            boolean hasThermalDampeners = false;
            if (entity instanceof Player player) {
                var skillsMap = player.getData(ModAttachments.ALL_SKILLS);
                SkillState thermalSkill = skillsMap.get(Skill.THERMAL_DAMPENERS);
                hasThermalDampeners = thermalSkill != null && thermalSkill.isUnlocked();
            }

            // Determine render type
            RenderType renderType = null;
            if (isVisor) {
                if (entity.getType().is(EntityTypeTags.UNDEAD)) {
                    renderType = InfraredShader.infraredUndeadGeneric(getTextureLocation(entity));
                } else {
                    renderType = InfraredShader.infraredEntityGeneric(getTextureLocation(entity));
                }
            }

            if (hasThermalDampeners) {
                InfraredShader.INFRARED_ENTITY_SHADER.getUniform("u_Heat").set(-3f);
            }

            // Cloak mode: skip rendering if renderType is null
            if (renderType != null) {
                VertexConsumer vc = buffer.getBuffer(renderType);
                model.renderToBuffer(poseStack, vc, 0xF000F, 0xFFFFFFFF, 0xFF0000FF);
            }

            // Render layers if not spectator
            if (!entity.isSpectator()) {
                for (RenderLayer<T, M> layer : layers) {
                    layer.render(poseStack, buffer, packedLight, entity,
                            walkPos, walkSpeed, partialTicks, bob, yawDelta, pitch);
                }
            }
            poseStack.popPose();

            if (hasThermalDampeners) {
                InfraredShader.INFRARED_ENTITY_SHADER.getUniform("u_Heat").set(0f);
            }
            // Post-render event
            NeoForge.EVENT_BUS.post(new RenderLivingEvent.Post(entity,
                    (LivingEntityRenderer<?, ?>) (Object) this, partialTicks,
                    poseStack, buffer, packedLight));
        }
        ci.cancel();
    }
}
