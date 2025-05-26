package com.elfoteo.crysis.mixins.nanosuit;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.util.*;
import com.elfoteo.crysis.attachments.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public abstract class NanosuitPlayerRenderer {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void modifyPlayerRendering(AbstractClientPlayer entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity == null)
            return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null)
            return;

        if (entity == mc.player) RenderState.isRenderingMainPlayer = true;
        RenderState.currentlyRenderingPlayer = entity;

        if (SuitUtils.isWearingFullNanosuit(player)
                && player.getData(ModAttachments.SUIT_MODE) == SuitModes.CLOAK.get()) {
            if (entity == mc.player) {
                // Hide other players with the effect
//                int r = 200;
//                int g = 200;
//                int b = 200;
//                int alpha = (int) (0.5f * 255);
//
//                MultiBufferSource translucentBuffer = new CustomColoredMultiBufferSource(buffer, r, g, b, alpha,
//                        textureLocation);
            }
            else {
                ci.cancel();
            }
            // Local player: allow default rendering to proceed
        }
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
            cir.setReturnValue(ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "textures/entity/nanosuit.png"));
        }
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/resources/ResourceLocation;", at=@At("HEAD"), cancellable = true)
    private void getTextureLocation(Entity entity, CallbackInfoReturnable<ResourceLocation> cir) {
        if (entity instanceof AbstractClientPlayer player && SuitUtils.isWearingFullNanosuit(player)){
            cir.setReturnValue(ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "textures/entity/nanosuit.png"));
        }
    }
}
