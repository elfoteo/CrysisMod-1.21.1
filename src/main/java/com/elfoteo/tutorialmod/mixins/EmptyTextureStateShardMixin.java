package com.elfoteo.tutorialmod.mixins;

import com.elfoteo.tutorialmod.util.IEmptyTextureStateAccessor;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;

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
