package com.elfoteo.crysis.mixins.accessors;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType.OutlineProperty;
import com.google.common.collect.ImmutableList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeState")
public interface ICompositeStateAccessor {
    
    @Accessor("textureState")
    RenderStateShard.EmptyTextureStateShard getTextureState();

    @Accessor("shaderState")
    RenderStateShard.ShaderStateShard getShaderState();

    @Accessor("transparencyState")
    RenderStateShard.TransparencyStateShard getTransparencyState();

    @Accessor("depthTestState")
    RenderStateShard.DepthTestStateShard getDepthTestState();

    @Accessor("cullState")
    RenderStateShard.CullStateShard getCullState();

    @Accessor("lightmapState")
    RenderStateShard.LightmapStateShard getLightmapState();

    @Accessor("overlayState")
    RenderStateShard.OverlayStateShard getOverlayState();

    @Accessor("layeringState")
    RenderStateShard.LayeringStateShard getLayeringState();

    @Accessor("outputState")
    RenderStateShard.OutputStateShard getOutputState();

    @Accessor("texturingState")
    RenderStateShard.TexturingStateShard getTexturingState();

    @Accessor("writeMaskState")
    RenderStateShard.WriteMaskStateShard getWriteMaskState();

    @Accessor("lineState")
    RenderStateShard.LineStateShard getLineState();

    @Accessor("colorLogicState")
    RenderStateShard.ColorLogicStateShard getColorLogicState();

    @Accessor("outlineProperty")
    OutlineProperty getOutlineProperty();

    @Accessor("states")
    ImmutableList<RenderStateShard> getStates();
}
