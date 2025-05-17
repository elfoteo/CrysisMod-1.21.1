package com.elfoteo.tutorialmod.mixins;

import com.elfoteo.tutorialmod.util.*;
import com.elfoteo.tutorialmod.attachments.*;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class NanosuitPlayerRenderer {

    @Unique
    private static final Int2IntOpenHashMap nanosuitMod$fadeOutCounters = new Int2IntOpenHashMap();

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void modifyPlayerRendering(AbstractClientPlayer entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity == null)
            return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null)
            return;

        int playerId = entity.getId();

        if (SuitUtils.isWearingFullNanosuit(player)
                && player.getData(ModAttachments.SUIT_MODE) == SuitModes.CLOAK.get()) {
            nanosuitMod$fadeOutCounters.put(playerId, 5);
            if (entity != mc.player) {
                // Hide other players with the effect
                ci.cancel();
            }
            // Local player: allow default rendering to proceed
        } else {
            int counter = nanosuitMod$fadeOutCounters.get(playerId);
            if (counter > 0) {
                nanosuitMod$fadeOutCounters.put(playerId, counter - 1);
                ci.cancel();
            } else {
                nanosuitMod$fadeOutCounters.remove(playerId);
            }
        }
    }
}
