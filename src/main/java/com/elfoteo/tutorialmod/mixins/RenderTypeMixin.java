package com.elfoteo.tutorialmod.mixins;

import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.InfraredShader;
import com.elfoteo.tutorialmod.util.SuitModes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(RenderType.class)
public abstract class RenderTypeMixin {

    @Inject(method = "entityCutout", at=@At("HEAD"), cancellable = true)
    private static void entityCutout(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        if (InfraredShader.INFRARED_ENTITY_CUTOUT_NO_CULL != null && Nanosuit.currentClientMode == SuitModes.VISOR.get())
            cir.setReturnValue(InfraredShader.infraredEntityCutoutNoCull(location));
    }

    @Inject(method = "outline(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;", at=@At("HEAD"), cancellable = true)
    private static void outline(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        cir.setReturnValue(InfraredShader.CompositeRenderType.OUTLINE.apply(location, RenderType.NO_CULL));
    }
}
