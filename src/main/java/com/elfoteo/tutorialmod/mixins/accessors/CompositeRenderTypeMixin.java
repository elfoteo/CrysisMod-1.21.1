package com.elfoteo.tutorialmod.mixins.accessors;

import com.elfoteo.tutorialmod.util.ICompositeRenderType;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public interface CompositeRenderTypeMixin extends ICompositeRenderType {

    @Accessor("state")
    RenderType.CompositeState getState();

    @Accessor("outline")
    Optional<RenderType> getOutline();

    @Accessor("isOutline")
    boolean getIsOutline();
}
