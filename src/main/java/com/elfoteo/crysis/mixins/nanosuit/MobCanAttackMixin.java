package com.elfoteo.crysis.mixins.nanosuit;

import com.elfoteo.crysis.util.SuitUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MobCanAttackMixin {
    @Inject(
            method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void canAttack(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity attacker = ((LivingEntity) (Object) this);
        if (attacker instanceof Player) return;
        if (target instanceof Player player) {
            double distSq = player.distanceToSqr(attacker);
            if (SuitUtils.isCloaked(player) && distSq >= 3.0 * 3.0) {
                // If player is “cloaked,” mobs should not attack at all.
                cir.setReturnValue(false);
            }
        }
    }
}
