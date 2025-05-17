package com.elfoteo.tutorialmod.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;

/**
 * Wrapper class to mark the buffer as already wrapped.
 */
// Custom MultiBufferSource to apply translucency
public class CustomColoredMultiBufferSource implements MultiBufferSource {
    private final MultiBufferSource original;
    private final int a;
    private final int r;
    private final int g;
    private final int b;
    private final ResourceLocation texture;

    public CustomColoredMultiBufferSource(MultiBufferSource original, int r, int g, int b, int a) {
        this.original = original;
        this.a = a;
        this.r = r;
        this.b = b;
        this.g = g;
        texture = null;
    }

    public CustomColoredMultiBufferSource(MultiBufferSource original, int r, int g, int b, int a,
            ResourceLocation texture) {
        this.original = original;
        this.a = a;
        this.r = r;
        this.b = b;
        this.g = g;
        this.texture = texture;
    }

    @Override
    public @NotNull VertexConsumer getBuffer(RenderType renderType) {

        // Detect if the incoming RenderType is opaque or armor-related
        if (texture != null) {
            // Replace it with a translucent RenderType using the same texture
            renderType = RenderType.entityTranslucent(this.texture);
        }

        VertexConsumer originalConsumer = original.getBuffer(renderType);
        return new CustomColoredVertexConsumer(originalConsumer, r, g, b, a);
    }

}
