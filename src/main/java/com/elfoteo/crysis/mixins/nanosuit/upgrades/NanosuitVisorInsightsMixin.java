package com.elfoteo.crysis.mixins.nanosuit.upgrades;

import com.elfoteo.crysis.gui.NanosuitVisorInsights;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.util.SuitModes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(LivingEntity.class)
public class NanosuitVisorInsightsMixin {
    @Inject(method = "isCurrentlyGlowing", at=@At("HEAD"), cancellable = true)
    private void shouldEntityAppearGlowing(CallbackInfoReturnable<Boolean> cir){
        LivingEntity self = (LivingEntity) (Object) this; // Safe cast in Mixin context
        if (self.level().isClientSide && Nanosuit.currentClientMode == SuitModes.VISOR.get() && NanosuitVisorInsights.getLookedAtEntity() == self){
            cir.setReturnValue(true);
        }
    }
}
