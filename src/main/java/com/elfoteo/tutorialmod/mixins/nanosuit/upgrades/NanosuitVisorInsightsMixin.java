package com.elfoteo.tutorialmod.mixins.nanosuit.upgrades;

import com.elfoteo.tutorialmod.gui.NanosuitVisorInsights;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class NanosuitVisorInsightsMixin {
    @Inject(method = "isCurrentlyGlowing", at=@At("HEAD"), cancellable = true)
    private void shouldEntityAppearGlowing(CallbackInfoReturnable<Boolean> cir){ // TODO: Visor insights
        LivingEntity self = (LivingEntity) (Object) this; // ? Safe cast in Mixin context
        if (NanosuitVisorInsights.getLookedAtEntity() == self){
            cir.setReturnValue(true);
        }
    }
}
