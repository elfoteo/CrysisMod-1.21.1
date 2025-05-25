package com.elfoteo.tutorialmod.mixins;

import com.elfoteo.tutorialmod.mixins.accessors.ICompositeStateAccessor;
import com.elfoteo.tutorialmod.util.ICompositeRenderType;
import com.elfoteo.tutorialmod.util.IEmptyTextureStateAccessor;
import com.elfoteo.tutorialmod.util.InfraredShader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Optional;
import java.util.function.BiFunction;

@Mixin(RenderType.CompositeRenderType.class)
public abstract class MixinRenderType_CompositeRenderType extends RenderType {

    private static final BiFunction<ResourceLocation, RenderStateShard.CullStateShard, RenderType> CUSTOM_OUTLINE = Util.memoize(
            (texture, cullState) -> RenderType.create(
                    "outline",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    1536,
                    CompositeState.builder()
                            .setShaderState(InfraredShader.RENDERTYPE_OUTLINE_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                            .setCullState(cullState)
                            .setDepthTestState(NO_DEPTH_TEST)
                            .setOutputState(OUTLINE_TARGET)
                            .createCompositeState(RenderType.OutlineProperty.IS_OUTLINE)
            )
    );

    /**
     * @author elfoteo
     * @reason idk
     */
    @Overwrite
    public Optional<RenderType> outline() {
        RenderType.CompositeState state = ((ICompositeRenderType) this).getState();

        // Cast to accessor interface
        ICompositeStateAccessor accessor = (ICompositeStateAccessor) (Object) state;

        if (accessor.getOutlineProperty() == RenderType.OutlineProperty.AFFECTS_OUTLINE) {
            // Get texture via accessor
            IEmptyTextureStateAccessor textureAccessor = (IEmptyTextureStateAccessor) accessor.getTextureState();
            Optional<ResourceLocation> textureOpt = textureAccessor.callCutoutTexture();

            return textureOpt.map(texture -> CUSTOM_OUTLINE.apply(texture, accessor.getCullState()));
        }
        return Optional.empty();
    }

    private MixinRenderType_CompositeRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                                boolean affectsCrumbling, boolean sortOnUpload) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, () -> {}, () -> {});
    }
}
