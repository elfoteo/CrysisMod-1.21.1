package com.elfoteo.tutorialmod.mixins;

import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.InfraredShader;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
