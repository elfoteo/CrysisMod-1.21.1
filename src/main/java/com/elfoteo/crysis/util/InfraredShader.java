package com.elfoteo.crysis.util;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.mixins.accessors.ICompositeStateAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = CrysisMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class InfraredShader {
    public static ParticleRenderType TERRAIN_SHEET = new ParticleRenderType() {
        public BufferBuilder begin(Tesselator p_350993_, TextureManager p_107442_) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
            RenderSystem.setShader(() -> INFRARED_PARTICLE_SHADER);
            return p_350993_.begin(Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        public String toString() {
            return "TERRAIN_SHEET";
        }
    };
    public static ParticleRenderType PARTICLE_SHEET_OPAQUE = new ParticleRenderType() {
        public BufferBuilder begin(Tesselator p_350576_, TextureManager p_107449_) {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.setShader(() -> INFRARED_PARTICLE_SHADER);
            return p_350576_.begin(Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        public String toString() {
            return "PARTICLE_SHEET_OPAQUE";
        }

        public boolean isTranslucent() {
            return false;
        }
    };
    public static ParticleRenderType PARTICLE_SHEET_TRANSLUCENT = new ParticleRenderType() {
        public BufferBuilder begin(Tesselator p_350826_, TextureManager p_107456_) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(() -> INFRARED_PARTICLE_SHADER);
            return p_350826_.begin(Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        public String toString() {
            return "PARTICLE_SHEET_TRANSLUCENT";
        }
    };
    public static ParticleRenderType PARTICLE_SHEET_LIT = new ParticleRenderType() {
        public BufferBuilder begin(Tesselator p_351047_, TextureManager p_107463_) {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.setShader(() -> INFRARED_PARTICLE_SHADER);
            return p_351047_.begin(Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        public String toString() {
            return "PARTICLE_SHEET_LIT";
        }

        public boolean isTranslucent() {
            return false;
        }
    };
    public static ParticleRenderType CUSTOM = new ParticleRenderType() {
        public BufferBuilder begin(Tesselator p_350910_, TextureManager p_107470_) {
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> INFRARED_PARTICLE_SHADER);
            return p_350910_.begin(Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        public String toString() {
            return "CUSTOM";
        }
    };
    public static ParticleRenderType NO_RENDER = new ParticleRenderType() {
        @Nullable
        public BufferBuilder begin(Tesselator p_350814_, TextureManager p_107477_) {
            return null;
        }

        public String toString() {
            return "NO_RENDER";
        }
    };

    public static ShaderInstance RENDERTYPE_OUTLINE;
    public static RenderStateShard.ShaderStateShard RENDERTYPE_OUTLINE_SHADER;

    public static RenderType INFRARED_SOLID_RENDERTYPE;
    public static ShaderInstance INFRARED_SOLID_SHADER;
    public static ShaderInstance INFRARED_ENTITY_SHADER;
    public static ShaderInstance INFRARED_UNDEAD_SHADER;
    public static ShaderInstance INFRARED_ARMOR_SHADER;
    public static ShaderInstance NANOSUIT_OVERLAY_SHADER;
    public static ShaderInstance INFRARED_ITEM_SHADER;
    public static ShaderInstance INFRARED_PARTICLE_SHADER;
    public static RenderStateShard.ShaderStateShard INFRARED_SOLID_SHADER_SHARD;
    public static RenderStateShard.ShaderStateShard INFRARED_ARMOR_SHADER_SHARD;
    public static RenderStateShard.ShaderStateShard INFRARED_ITEM_SHADER_SHARD;

    public static BiFunction<ResourceLocation, Boolean, RenderType> INFRARED_RENDER_TYPE_ENTITY_GENERIC;
    private static BiFunction<ResourceLocation, Boolean, RenderType> INFRARED_RENDER_TYPE_UNDEAD_GENERIC;
    private static BiFunction<ResourceLocation, Boolean, RenderType> INFRARED_ENTITY_TRANSLUCENT;
    private static BiFunction<ResourceLocation, Boolean, RenderType> INFRARED_ENTITY_CUTOUT_NO_CULL_Z_OFFSET;
    private static Function<ResourceLocation, RenderType> ARMOR_INFRARED_RENDER_TYPE;
    private static Function<ResourceLocation, RenderType> NANOSUIT_OVERLAY_RENDER_TYPE;
    private static Function<ResourceLocation, RenderType> INFRARED_ENTITY_TRANSLUCENT_CULL_FOR_ITEMS;
    public static BiFunction<ResourceLocation, Boolean, RenderType> INFRARED_ENTITY_CUTOUT_NO_CULL;
    public static BiFunction<ResourceLocation, Boolean, RenderType> INFRARED_BLOCK;

    public static BiFunction<ResourceLocation, Boolean, RenderType> TARGET_INDICATOR;

    @OnlyIn(Dist.CLIENT)
    @EventBusSubscriber(modid = CrysisMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class Blocks {

        public static ShaderInstance SOLID_SHADER;
        public static ShaderInstance CUTOUT_SHADER;
        public static ShaderInstance CUTOUT_MIPPED_SHADER;
        public static ShaderInstance TRANSLUCENT_SHADER;
        public static ShaderInstance TRIPWIRE_SHADER;

        @SubscribeEvent
        public static void onRegisterShaders(RegisterShadersEvent event) {
            try {
                SOLID_SHADER = new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "blocks/rendertype_solid"),
                        DefaultVertexFormat.BLOCK);

                CUTOUT_SHADER = new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "blocks/rendertype_cutout"),
                        DefaultVertexFormat.BLOCK);

                CUTOUT_MIPPED_SHADER = new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "blocks/rendertype_cutout_mipped"),
                        DefaultVertexFormat.BLOCK);

                TRANSLUCENT_SHADER = new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "blocks/rendertype_translucent"),
                        DefaultVertexFormat.BLOCK);

                TRIPWIRE_SHADER = new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "blocks/rendertype_tripwire"),
                        DefaultVertexFormat.BLOCK);

            } catch (IOException e) {
                throw new RuntimeException("Failed to load block shaders", e);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            TARGET_INDICATOR = Util.memoize((p_286166_, p_286167_) -> {
                CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                        .setShaderState(RenderType.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(p_286166_, false, false))
                        .setTransparencyState(RenderType.NO_TRANSPARENCY)
                        .setCullState(RenderType.NO_CULL)
                        .setDepthTestState(RenderType.NO_DEPTH_TEST)
                        .setLightmapState(RenderType.LIGHTMAP)
                        .setOverlayState(RenderType.OVERLAY)
                        .createCompositeState(p_286167_);
                return RenderType.create("entity_cutout_no_cull", DefaultVertexFormat.NEW_ENTITY, Mode.QUADS, 1536, true, false, rendertype$compositestate);
            });

            INFRARED_SOLID_SHADER = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "infrared_entity"),
                    DefaultVertexFormat.POSITION_COLOR);
            INFRARED_SOLID_SHADER_SHARD = new RenderStateShard.ShaderStateShard(() -> INFRARED_SOLID_SHADER);

            RENDERTYPE_OUTLINE = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "rendertype_outline"),
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            RENDERTYPE_OUTLINE_SHADER = new RenderStateShard.ShaderStateShard(() -> RENDERTYPE_OUTLINE);

            INFRARED_ENTITY_SHADER = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "infrared_entity"),
                    DefaultVertexFormat.POSITION_COLOR);

            INFRARED_UNDEAD_SHADER = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "infrared_entity"),
                    DefaultVertexFormat.POSITION_COLOR);

            INFRARED_UNDEAD_SHADER.safeGetUniform("u_Heat").set(-0.56f);

            INFRARED_ARMOR_SHADER = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "infrared_armor_layer"),
                    DefaultVertexFormat.NEW_ENTITY);
            INFRARED_ARMOR_SHADER_SHARD = new RenderStateShard.ShaderStateShard(() -> INFRARED_ARMOR_SHADER);

            NANOSUIT_OVERLAY_SHADER = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "nanosuit_overlay_layer"),
                    DefaultVertexFormat.NEW_ENTITY);

            INFRARED_ITEM_SHADER = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "infrared_item"),
                    DefaultVertexFormat.NEW_ENTITY);
            INFRARED_ITEM_SHADER_SHARD = new RenderStateShard.ShaderStateShard(() -> INFRARED_ITEM_SHADER);

            INFRARED_PARTICLE_SHADER = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "infrared_particle"),
                    DefaultVertexFormat.PARTICLE);

            INFRARED_SOLID_RENDERTYPE = RenderType.create(
                    "infrared_solid_render_type",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false,
                    false,
                    CompositeState.builder()
                            .setShaderState(INFRARED_SOLID_SHADER_SHARD)
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                            .createCompositeState(true)
            );

            INFRARED_RENDER_TYPE_ENTITY_GENERIC = Util.memoize((p_286166_, p_286167_) -> {
                CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> INFRARED_ENTITY_SHADER))
                        .setTextureState(new RenderStateShard.TextureStateShard(p_286166_, false, false))
                        .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderType.NO_CULL)
                        .setLightmapState(RenderType.LIGHTMAP)
                        .setOverlayState(RenderType.OVERLAY)
                        .setDepthTestState(RenderType.NO_DEPTH_TEST)
                        .setCullState(RenderType.CULL)
                        .createCompositeState(p_286167_);
                return RenderType.create("infrared_entity_generic", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, rendertype$compositestate);
            });

            INFRARED_RENDER_TYPE_UNDEAD_GENERIC = Util.memoize((p_286166_, p_286167_) -> {
                CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> INFRARED_UNDEAD_SHADER))
                        .setTextureState(new RenderStateShard.TextureStateShard(p_286166_, false, false))
                        .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderType.NO_CULL)
                        .setLightmapState(RenderType.LIGHTMAP)
                        .setOverlayState(RenderType.OVERLAY)
                        .setDepthTestState(RenderType.NO_DEPTH_TEST)
                        .setCullState(RenderType.CULL)
                        .createCompositeState(p_286167_);
                return RenderType.create("infrared_entity_generic", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, rendertype$compositestate);
            });

            INFRARED_ENTITY_TRANSLUCENT_CULL_FOR_ITEMS = Util.memoize((p_286165_) -> {
                CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                        .setShaderState(INFRARED_ITEM_SHADER_SHARD)
                        .setTextureState(new RenderStateShard.TextureStateShard(p_286165_, false, false))
                        .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(RenderType.LIGHTMAP)
                        .setOverlayState(RenderType.OVERLAY)
                        .setDepthTestState(RenderType.NO_DEPTH_TEST)
                        .createCompositeState(true);
                return RenderType.create("infrared_entity_translucent_cull", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true, rendertype$compositestate);
            });

            INFRARED_ENTITY_CUTOUT_NO_CULL = Util.memoize((p_286166_, p_286167_) -> {
                CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                        .setShaderState(INFRARED_ARMOR_SHADER_SHARD) // TODO: Replace with proper shader if needed
                        .setTextureState(new RenderStateShard.TextureStateShard(p_286166_, false, false))
                        .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderType.NO_CULL)
                        .setLightmapState(RenderType.LIGHTMAP)
                        .setOverlayState(RenderType.OVERLAY)
                        .setDepthTestState(RenderType.NO_DEPTH_TEST)
                        .createCompositeState(p_286167_);
                return RenderType.create("infrared_entity_cutout_no_cull", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, rendertype$compositestate);
            });

            INFRARED_BLOCK = Util.memoize((p_286166_, p_286167_) -> {
                CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> Blocks.SOLID_SHADER)) // TODO: Replace with proper shader if needed
                        .setTextureState(new RenderStateShard.TextureStateShard(p_286166_, false, false))
                        .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderType.NO_CULL)
                        .setLightmapState(RenderType.LIGHTMAP)
                        .setOverlayState(RenderType.OVERLAY)
                        .createCompositeState(p_286167_);
                return RenderType.create("infrared_block", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, rendertype$compositestate);
            });

            ARMOR_INFRARED_RENDER_TYPE = Util.memoize((textureLocation) -> createInfraredArmorCutoutNoCull("armor_infrared_render_type", textureLocation));
            NANOSUIT_OVERLAY_RENDER_TYPE = Util.memoize((textureLocation) -> createNanosuitOverlay("nanosuit_overlay_render_type", textureLocation));

            INFRARED_ENTITY_TRANSLUCENT = Util.memoize((p_286156_, p_286157_) -> {
                CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> Blocks.SOLID_SHADER))
                        .setTextureState(new RenderStateShard.TextureStateShard(p_286156_, false, false))
                        .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderType.NO_CULL)
                        .setLightmapState(RenderType.LIGHTMAP)
                        .setOverlayState(RenderType.OVERLAY)
                        .createCompositeState(p_286157_);
                return RenderType.create("infrared_entity_translucent", DefaultVertexFormat.NEW_ENTITY, Mode.QUADS, 1536, true, true, rendertype$compositestate);
            });

            INFRARED_ENTITY_CUTOUT_NO_CULL_Z_OFFSET = Util.memoize((p_286153_, p_286154_) -> {
                CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> Blocks.SOLID_SHADER))
                        .setTextureState(new RenderStateShard.TextureStateShard(p_286153_, false, false))
                        .setTransparencyState(RenderType.NO_TRANSPARENCY)
                        .setCullState(RenderType.NO_CULL)
                        .setLightmapState(RenderType.LIGHTMAP)
                        .setOverlayState(RenderType.OVERLAY)
                        .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(p_286154_);
                return RenderType.create("entity_cutout_no_cull_z_offset", DefaultVertexFormat.NEW_ENTITY, Mode.QUADS, 1536, true, false, rendertype$compositestate);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This method returns the currently‐active infrared entity shader instance.
     * Use this as the supplier for RenderSystem.setShader().
     */
    @OnlyIn(Dist.CLIENT)
    public static ShaderInstance getInfraredProgram() {
        if (INFRARED_ENTITY_SHADER == null) {
            throw new IllegalStateException("InfraredShader: INFRARED_ENTITY_SHADER not yet initialized!");
        }
        return INFRARED_ENTITY_SHADER;
    }

    public static RenderType infraredEntityCutoutNoCull(ResourceLocation location, boolean outline) {
        return INFRARED_ENTITY_CUTOUT_NO_CULL.apply(location, outline);
    }

    public static RenderType infraredEntityCutoutNoCull(ResourceLocation location) {
        return infraredEntityCutoutNoCull(location, true);
    }

    public static RenderType infraredBlock(ResourceLocation location) {
        return INFRARED_BLOCK.apply(location, true);
    }

    public static RenderType infraredEntityGeneric(ResourceLocation location) {
        return INFRARED_RENDER_TYPE_ENTITY_GENERIC.apply(location, true);
    }

    public static RenderType infraredUndeadGeneric(ResourceLocation location) {
        return INFRARED_RENDER_TYPE_UNDEAD_GENERIC.apply(location, true);
    }

    public static RenderType infraredEntityTranslucentCull_for_items(ResourceLocation location) {
        return INFRARED_ENTITY_TRANSLUCENT_CULL_FOR_ITEMS.apply(location);
    }

    public static RenderType infraredArmorCutoutNoCull(ResourceLocation location) {
        return ARMOR_INFRARED_RENDER_TYPE.apply(location);
    }

    public static RenderType nanosuitOverlay(ResourceLocation location) {
        return NANOSUIT_OVERLAY_RENDER_TYPE.apply(location);
    }

    private static CompositeRenderType createInfraredArmorCutoutNoCull(String name, ResourceLocation id) {
        CompositeState compositestate = RenderType.CompositeState.builder()
                .setShaderState(INFRARED_ARMOR_SHADER_SHARD)
                .setTextureState(new RenderStateShard.TextureStateShard(id, false, false))
                .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                .setCullState(RenderType.NO_CULL)
                .setLightmapState(RenderType.LIGHTMAP)
                .setOverlayState(RenderType.OVERLAY)
                .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING)
                .setDepthTestState(RenderType.NO_DEPTH_TEST)
                .createCompositeState(true);

        return new CompositeRenderType(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, compositestate);
    }

    private static CompositeRenderType createNanosuitOverlay(String name, ResourceLocation id) {
        boolean equalDepthTest = false;
        CompositeState rendertype$compositestate = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> NANOSUIT_OVERLAY_SHADER))
                .setTextureState(new RenderStateShard.TextureStateShard(id, false, false))
                .setTransparencyState(RenderType.NO_TRANSPARENCY)
                .setCullState(RenderType.NO_CULL)
                .setLightmapState(RenderType.LIGHTMAP)
                .setOverlayState(RenderType.OVERLAY)
                .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING)
                .setDepthTestState(equalDepthTest ? RenderType.EQUAL_DEPTH_TEST : RenderType.LEQUAL_DEPTH_TEST).createCompositeState(true);
        return new CompositeRenderType(name, DefaultVertexFormat.NEW_ENTITY, Mode.QUADS, 1536, true, false, rendertype$compositestate);
    }

    public static RenderType targetIndicator(ResourceLocation location) {
        return TARGET_INDICATOR.apply(location, true);
    }

    public static RenderType infraredEntityTranslucent(ResourceLocation location, boolean outline) {
        return INFRARED_ENTITY_TRANSLUCENT.apply(location, outline);
    }

    public static RenderType infraredEntityTranslucent(ResourceLocation location) {
        return infraredEntityTranslucent(location, true);
    }

    public static RenderType infraredEntityCutoutNoCullZOffset(ResourceLocation location, boolean outline) {
        return INFRARED_ENTITY_CUTOUT_NO_CULL_Z_OFFSET.apply(location, outline);
    }

    public static RenderType infraredEntityCutoutNoCullZOffset(ResourceLocation location) {
        return infraredEntityCutoutNoCullZOffset(location, true);
    }

    @OnlyIn(Dist.CLIENT)
    public static final class CompositeRenderType extends RenderType {
        public static final BiFunction<ResourceLocation, RenderStateShard.CullStateShard, RenderType> OUTLINE = Util.memoize(
                (texture, cull) -> RenderType.create("custom_outline", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536,
                        RenderType.CompositeState.builder()
                                .setShaderState(InfraredShader.RENDERTYPE_OUTLINE_SHADER)
                                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                                .setCullState(cull)
                                .setDepthTestState(NO_DEPTH_TEST)
                                .createCompositeState(true)
                )
        );

        private final CompositeState state;
        private final Optional<RenderType> outline;
        private final boolean isOutline;

        // Helper static methods to create setup and clear runnables before super() call
        private static Runnable createSetupRunnable(CompositeState state) {
            ICompositeStateAccessor accessor = (ICompositeStateAccessor) (Object) state;
            return () -> accessor.getStates().forEach(RenderStateShard::setupRenderState);
        }

        private static Runnable createClearRunnable(CompositeState state) {
            ICompositeStateAccessor accessor = (ICompositeStateAccessor) (Object) state;
            return () -> accessor.getStates().forEach(RenderStateShard::clearRenderState);
        }

        CompositeRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                            boolean affectsCrumbling, boolean sortOnUpload, CompositeState state) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload,
                    createSetupRunnable(state), createClearRunnable(state));

            this.state = state;

            ICompositeStateAccessor accessor = (ICompositeStateAccessor) (Object) state;
            IEmptyTextureStateAccessor textureAccessor = (IEmptyTextureStateAccessor) accessor.getTextureState();
            Optional<ResourceLocation> textureOpt = textureAccessor.callCutoutTexture();

            this.outline = accessor.getOutlineProperty() == RenderType.OutlineProperty.AFFECTS_OUTLINE
                    ? textureOpt.map(texture -> OUTLINE.apply(texture, accessor.getCullState()))
                    : Optional.empty();

            this.isOutline = accessor.getOutlineProperty() == RenderType.OutlineProperty.IS_OUTLINE;
        }

        public Optional<RenderType> outline() {
            return this.outline;
        }

        public boolean isOutline() {
            return this.isOutline;
        }

        @Override
        public String toString() {
            return "RenderType[" + this.name + ":" + this.state + "]";
        }
    }
}