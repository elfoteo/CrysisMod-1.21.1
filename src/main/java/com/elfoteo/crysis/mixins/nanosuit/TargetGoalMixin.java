package com.elfoteo.crysis.mixins.nanosuit;

import com.elfoteo.crysis.util.SuitUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(TargetGoal.class)
public abstract class TargetGoalMixin {
    @Shadow @Nullable protected LivingEntity targetMob;
    @Shadow @Final protected Mob mob;

    /**
     * When the goal tests “canAttack(potentialTarget, predicate)”,
     * block cloaked players at ≥3 blocks distance.
     */
    @Inject(
            method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onCanAttack(LivingEntity potentialTarget, TargetingConditions targetPredicate, CallbackInfoReturnable<Boolean> cir) {
        if (potentialTarget instanceof Player player && SuitUtils.isWearingFullNanosuit(player)) {
            Mob attacker = this.mob;
            double distSq = attacker.distanceToSqr(player);

            // If farther than 3 blocks, cancel targeting. Otherwise allow below 3.
            if (distSq >= 3.0 * 3.0) {
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * While a mob is already chasing a target, this runs each tick to see
     * if it “canContinueToUse()”. We clear/stop if target is a cloaked player
     * at ≥3 blocks. If <3 blocks, we allow the chase to continue.
     */
    @Inject(
            method = "canContinueToUse()Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onCanContinueToUse(CallbackInfoReturnable<Boolean> cir) {
        Mob attacker = this.mob;
        LivingEntity current = attacker.getTarget();
        if (current == null) {
            current = this.targetMob;
        }

        if (current instanceof Player player && SuitUtils.isCloaked(player)) {
            double distSq = attacker.distanceToSqr(player);

            // If farther than 3 blocks, clear the target and cancel this goal.
            if (distSq >= 3.0 * 3.0) {
                attacker.setTarget(null);
                this.targetMob = null;
                cir.setReturnValue(false);
            }
            // If <3 blocks, do nothing here → vanilla will continue the chase.
        }
    }
}
