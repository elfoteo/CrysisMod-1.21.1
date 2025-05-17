package com.elfoteo.tutorialmod.util;

import net.minecraft.client.renderer.RenderType;
import java.util.Optional;

public interface ICompositeRenderType {
    RenderType.CompositeState getState();
    Optional<RenderType> getOutline();
    boolean getIsOutline();
}