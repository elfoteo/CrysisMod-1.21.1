package com.elfoteo.tutorialmod.mixins.accessors;

import com.elfoteo.tutorialmod.util.IEmptyTextureStateAccessor;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(RenderStateShard.EmptyTextureStateShard.class)
public abstract class EmptyTextureStateShardMixin implements IEmptyTextureStateAccessor {

    @Shadow
    protected abstract Optional<ResourceLocation> cutoutTexture();

    @Override
    public Optional<ResourceLocation> callCutoutTexture() {
        return cutoutTexture();
    }
}
