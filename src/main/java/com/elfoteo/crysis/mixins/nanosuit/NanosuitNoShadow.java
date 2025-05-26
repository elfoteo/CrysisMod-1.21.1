package com.elfoteo.crysis.mixins.nanosuit;

import com.elfoteo.crysis.nanosuit.Nanosuit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.elfoteo.crysis.util.*;
import com.elfoteo.crysis.attachments.*;

/**
 * This mixin intercepts shadow rendering. When the player has the shadow
 * effect,
 * the shadow rendering is cancelled.
 */
@Mixin(EntityRenderDispatcher.class)
public class NanosuitNoShadow {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void cancelShadowRendering(PoseStack poseStack, MultiBufferSource buffer, Entity entity,
            float weight, float partialTicks, LevelReader level,
            float size, CallbackInfo ci) {
        // Check if the entity is a client player and has the shadow effect.
        if (entity instanceof Player player && SuitUtils.isWearingFullNanosuit(player)
                && player.getData(ModAttachments.SUIT_MODE) == SuitModes.CLOAK.get()) {
            ci.cancel();
        }
        if (Nanosuit.currentClientMode == SuitModes.VISOR.get()){
            ci.cancel();
        }
    }
}
