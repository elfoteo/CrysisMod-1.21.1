package com.elfoteo.crysis.mixins.nanosuit.infrared;

import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.util.SuitModes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    // Inject into the 4-arg renderToBuffer method
    @Inject(method = "getSkyColor",
            at = @At("HEAD"), cancellable = true)
    private void onRenderToBuffer(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (Nanosuit.currentClientMode != SuitModes.VISOR.get()) return;
        cir.setReturnValue(new Vec3(0, 0, 127)); // Prevent default behavior
        cir.cancel();
    }
}
