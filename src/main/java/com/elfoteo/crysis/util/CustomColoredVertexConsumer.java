package com.elfoteo.crysis.util;

import com.mojang.blaze3d.vertex.VertexConsumer;

public class CustomColoredVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final int r, g, b, a; // Store individual color components

    // Constructor accepts individual color components (r, g, b, a)
    public CustomColoredVertexConsumer(VertexConsumer delegate, int r, int g, int b, int a) {
        this.delegate = delegate;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a; // Set the alpha value directly
    }

    @Override
    public VertexConsumer addVertex(float v, float v1, float v2) {
        return delegate.addVertex(v, v1, v2);
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        // Use the color components provided in the constructor instead of the arguments
        return delegate.setColor(this.r, this.g, this.b, this.a);
    }

    @Override
    public VertexConsumer setColor(int color) {
        // If setColor with a single integer is called, use the RGB stored in the
        // constructor and the alpha value
        int packedColor = (this.a << 24) | (this.r << 16) | (this.g << 8) | this.b;
        return delegate.setColor(packedColor);
    }

    @Override
    public VertexConsumer setUv(float v, float v1) {
        return delegate.setUv(v, v1);
    }

    @Override
    public VertexConsumer setUv1(int i, int i1) {
        return delegate.setUv1(i, i1);
    }

    @Override
    public VertexConsumer setUv2(int i, int i1) {
        return delegate.setUv2(i, i1);
    }

    @Override
    public VertexConsumer setNormal(float v, float v1, float v2) {
        return delegate.setNormal(v, v1, v2);
    }
}
