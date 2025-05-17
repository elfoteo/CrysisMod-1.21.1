package com.elfoteo.tutorialmod.util;

import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

public interface SetSectionRenderDispatcher {
    void setSectionRenderDispatcher(SectionRenderDispatcher sr);

    RenderBuffers getRenderBuffers();
}
