package com.elfoteo.crysis.mixins.nanosuit;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.elfoteo.crysis.util.SuitModes;
import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.util.SuitUtils;

@Mixin(LivingEntity.class)
public class NanosuitNoParticles {
    @Redirect(method = "tickEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void redirectAddParticle(Level level, ParticleOptions particleData, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed) {
        LivingEntity entity = (LivingEntity) (Object) this;
        // For players, use the custom shadow effect tracking.
        if (entity instanceof Player player) {
            if (SuitUtils.isWearingFullNanosuit(player)
                    && player.getData(ModAttachments.SUIT_MODE) == SuitModes.CLOAK.get()) {
                return;
            }
        }
        level.addParticle(particleData, x, y, z, xSpeed, ySpeed, zSpeed);
    }
}
