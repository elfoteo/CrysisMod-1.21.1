package com.elfoteo.crysis.mixins.nanosuit;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.util.*;
import com.elfoteo.crysis.attachments.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public abstract class NanosuitPlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public NanosuitPlayerRenderer(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Shadow protected abstract void setModelProperties(AbstractClientPlayer clientPlayer);

    @Unique
    private static final ResourceLocation NANOSUIT_TEXTURE = ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "textures/entity/nanosuit.png");

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void modifyPlayerRendering(AbstractClientPlayer entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void resetRenderState(AbstractClientPlayer entity, float entityYaw, float partialTicks,
                                  PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        RenderState.isRenderingMainPlayer = false;
        RenderState.currentlyRenderingPlayer = null;
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;", at=@At("HEAD"), cancellable = true)
    private void getTextureLocation(AbstractClientPlayer entity, CallbackInfoReturnable<ResourceLocation> cir) {
        if (SuitUtils.isWearingFullNanosuit(entity)){
            cir.setReturnValue(NANOSUIT_TEXTURE);
        }
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/resources/ResourceLocation;", at=@At("HEAD"), cancellable = true)
    private void getTextureLocation(Entity entity, CallbackInfoReturnable<ResourceLocation> cir) {
        if (entity instanceof AbstractClientPlayer player && SuitUtils.isWearingFullNanosuit(player)){
            cir.setReturnValue(NANOSUIT_TEXTURE);
        }
    }

    @Inject(method = "renderHand", at=@At("HEAD"), cancellable = true)
    private void getTextureLocation(PoseStack poseStack, MultiBufferSource buffer, int combinedLight, AbstractClientPlayer player, ModelPart rendererArm, ModelPart rendererArmwear, CallbackInfo ci) {
        if (SuitUtils.isWearingFullNanosuit(player)){
            PlayerModel<AbstractClientPlayer> playermodel = this.getModel();
            this.setModelProperties(player);
            playermodel.attackTime = 0.0F;
            playermodel.crouching = false;
            playermodel.swimAmount = 0.0F;
            playermodel.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
            rendererArm.xRot = 0.0F;
            if (Nanosuit.currentClientMode == SuitModes.VISOR.get()){
                rendererArm.render(poseStack, buffer.getBuffer(InfraredShader.infraredEntityGeneric(NANOSUIT_TEXTURE)), combinedLight, OverlayTexture.NO_OVERLAY);
            }
            else {
                rendererArm.render(poseStack, buffer.getBuffer(RenderType.entitySolid(NANOSUIT_TEXTURE)), combinedLight, OverlayTexture.NO_OVERLAY);
            }
            rendererArmwear.xRot = 0.0F;
            if (Nanosuit.currentClientMode == SuitModes.VISOR.get()){
                rendererArmwear.render(poseStack, buffer.getBuffer(InfraredShader.infraredEntityGeneric(NANOSUIT_TEXTURE)), combinedLight, OverlayTexture.NO_OVERLAY);
            }
            else {
                rendererArmwear.render(poseStack, buffer.getBuffer(RenderType.entityTranslucent(NANOSUIT_TEXTURE)), combinedLight, OverlayTexture.NO_OVERLAY);
            }
            ci.cancel();
        }
    }
}
