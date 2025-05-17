package com.elfoteo.tutorialmod.mixins;

import com.elfoteo.tutorialmod.attachments.*;
import com.elfoteo.tutorialmod.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class NanosuitPowerJump {
    @Unique
    private static float JUMP_COST = 20;

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void onJump(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof Player player))
            return;
        if (!player.isCrouching())
            return;

        if (SuitUtils.tryDrainEnergy(player, JUMP_COST)) {
            // Get the player's current pitch (looking up or down)
            float pitch = entity.getXRot(); // Get the pitch directly
            float yaw = entity.getYRot(); // Get the yaw

            // Convert pitch and yaw to radians
            float pitchRadians = pitch * ((float) Math.PI / 180F);
            float yawRadians = yaw * ((float) Math.PI / 180F);

            // Calculate jump power based on pitch
            float jumpPowerY = -Mth.sin(pitchRadians) * 0.8f + 1f; // Vertical movement based on pitch
            if (jumpPowerY < 0)
                jumpPowerY = 0; // Prevent jumping below ground level

            // Adjust horizontal jump power based on pitch (less horizontal force if looking
            // up)
            float horizontalPowerFactor = Mth.cos(pitchRadians); // The more upwards, the less horizontal force

            // Horizontal jump direction (based on yaw)
            double jumpX = -Math.sin(yawRadians) * 1.5f * horizontalPowerFactor;
            double jumpZ = Math.cos(yawRadians) * 1.5f * horizontalPowerFactor;

            // Apply calculated jump power to entity's motion
            Vec3 deltaMovement = entity.getDeltaMovement();
            entity.setDeltaMovement(deltaMovement.x + jumpX, jumpPowerY, deltaMovement.z + jumpZ);

            entity.hasImpulse = true;
            CommonHooks.onLivingJump(entity);
            ci.cancel(); // Cancel the default jump to apply the custom one
        }
    }
}
