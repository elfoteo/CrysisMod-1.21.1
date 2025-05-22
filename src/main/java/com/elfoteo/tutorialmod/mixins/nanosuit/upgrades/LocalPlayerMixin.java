package com.elfoteo.tutorialmod.mixins.nanosuit.upgrades;

import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.skill.SkillData;
import com.elfoteo.tutorialmod.util.SuitUtils;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends Player {

    public LocalPlayerMixin(Level level, BlockPos pos, float yRot, GameProfile profile) {
        super(level, pos, yRot, profile);
    }

    /**
     * Redirects the call to `this.setSprinting(true)` inside aiStep(),
     * so you can block or replace the double-tap sprint behavior.
     */
    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;setSprinting(Z)V",
                    ordinal = 0
            )
    )
    private void disableDoubleTapSprint(LocalPlayer instance, boolean sprinting) {
        // Check nanosuit dash conditions
        if (SkillData.isUnlocked(Skill.DASH_JET, instance)
                && SuitUtils.isWearingFullNanosuit(instance)
                && SuitUtils.tryDrainEnergy(instance, 10f)) {

            Vec3 look = instance.getLookAngle();

            // Flatten Y to prevent high vertical dashing, but apply upward nudge
            Vec3 flatLook = new Vec3(look.x, 0, look.z).normalize();
            Vec3 dashVector = flatLook.add(0, 0.2, 0).normalize().scale(1.6);

            instance.push(dashVector);
            instance.hurtMarked = true; // Mark for velocity update
        }
    }
}
