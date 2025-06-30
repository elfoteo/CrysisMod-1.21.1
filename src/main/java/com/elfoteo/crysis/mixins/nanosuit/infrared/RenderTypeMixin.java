package com.elfoteo.crysis.mixins.nanosuit.infrared;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SuitModes;
import net.minecraft.client.Minecraft;
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
    @Inject(method = "outline(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;", at=@At("HEAD"), cancellable = true)
    private static void outline(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        cir.setReturnValue(InfraredShader.CompositeRenderType.OUTLINE.apply(location, RenderType.NO_CULL));
    }

    @Inject(method = "entitySolid", at=@At("HEAD"), cancellable = true)
    private static void entitySolid(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        if (InfraredShader.INFRARED_RENDER_TYPE_ENTITY_GENERIC == null) return;
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) != SuitModes.VISOR.get()) return;
        cir.setReturnValue(InfraredShader.infraredEntityGeneric(location));
    }

    @Inject(method = "entityCutout", at=@At("HEAD"), cancellable = true)
    private static void entityCutout(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        if (InfraredShader.INFRARED_RENDER_TYPE_ENTITY_GENERIC == null) return;
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) != SuitModes.VISOR.get()) return;
        cir.setReturnValue(InfraredShader.infraredEntityGeneric(location));
    }

    @Inject(method = "entityCutoutNoCull*", at=@At("HEAD"), cancellable = true)
    private static void entityCutoutNoCull(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        if (InfraredShader.INFRARED_ENTITY_CUTOUT_NO_CULL == null) return;
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) != SuitModes.VISOR.get()) return;
        cir.setReturnValue(InfraredShader.infraredEntityCutoutNoCull(location));
    }

    @Inject(method = "beaconBeam", at=@At("HEAD"), cancellable = true)
    private static void beaconBeam(ResourceLocation location, boolean colorFlag, CallbackInfoReturnable<RenderType> cir) {
        if (InfraredShader.INFRARED_BEACON_BEAM == null) return;
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) != SuitModes.VISOR.get()) return;
        cir.setReturnValue(InfraredShader.INFRARED_BEACON_BEAM.apply(location, colorFlag));
    }

    @Inject(method = "entityTranslucent(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;", at=@At("HEAD"), cancellable = true)
    private static void entityTranslucent(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        if (InfraredShader.INFRARED_ENTITY_SHADER == null) return;
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) != SuitModes.VISOR.get()) return;
        cir.setReturnValue(InfraredShader.INFRARED_RENDER_TYPE_ENTITY_GENERIC.apply(location, true));
    }

    @Inject(method = "entityTranslucent(Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/renderer/RenderType;", at=@At("HEAD"), cancellable = true)
    private static void entityTranslucent(ResourceLocation location, boolean outline, CallbackInfoReturnable<RenderType> cir) {
        if (InfraredShader.INFRARED_ENTITY_SHADER == null) return;
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) != SuitModes.VISOR.get()) return;
        cir.setReturnValue(InfraredShader.INFRARED_RENDER_TYPE_ENTITY_GENERIC.apply(location, outline));
    }
}
