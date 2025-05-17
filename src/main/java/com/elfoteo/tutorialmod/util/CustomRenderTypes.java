package com.elfoteo.tutorialmod.util;

import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.resources.ResourceLocation;

import com.elfoteo.tutorialmod.*;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

public class CustomRenderTypes {
    public static RenderType RED_XRAY = RenderType.create(
            "end_gateway",
            DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEndGatewayShader))
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setTextureState(
                            RenderStateShard.MultiTextureStateShard.builder()
                                    .add(TheEndPortalRenderer.END_SKY_LOCATION, false, false)
                                    .add(TheEndPortalRenderer.END_PORTAL_LOCATION, false, false)
                                    .build()
                    )
                    .createCompositeState(true)
    );

}
