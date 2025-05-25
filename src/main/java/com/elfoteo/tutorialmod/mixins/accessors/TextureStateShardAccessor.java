package com.elfoteo.tutorialmod.mixins.accessors;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

@Mixin(RenderStateShard.TextureStateShard.class)
public interface TextureStateShardAccessor {
    @Invoker("cutoutTexture")
    Optional<ResourceLocation> callCutoutTexture();
}
