package com.elfoteo.crysis.util;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public interface IEmptyTextureStateAccessor {
    Optional<ResourceLocation> callCutoutTexture();
}
