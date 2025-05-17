package com.elfoteo.tutorialmod.util;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.mixins.ICompositeStateAccessor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = TutorialMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class InfraredShader {
    public static ShaderInstance RED_SHADER;
    public static ShaderInstance INFRARED_ARMOR_SHADER;
    public static RenderStateShard.ShaderStateShard INFRARED_ARMOR_SHADER_SHARD;
    public static RenderType INFRARED_RENDER_TYPE;
    private static Function<ResourceLocation, RenderType> ARMOR_INFRARED_RENDER_TYPE;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            RED_SHADER = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "infrared_entity"),
                    DefaultVertexFormat.POSITION_COLOR);
            event.registerShader(RED_SHADER, (shader) -> RED_SHADER = shader);

            INFRARED_ARMOR_SHADER = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "infrared_armor_layer"),
                    DefaultVertexFormat.NEW_ENTITY);
            INFRARED_ARMOR_SHADER_SHARD = new RenderStateShard.ShaderStateShard(() -> INFRARED_ARMOR_SHADER);

            INFRARED_RENDER_TYPE = RenderType.create(
                    "red_render_type",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false,
                    false,
                    CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(() -> RED_SHADER))
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                            .createCompositeState(true)
            );

            ARMOR_INFRARED_RENDER_TYPE = Util.memoize((textureLocation) -> createInfraredArmorCutoutNoCull("armor_infrared_render_type", textureLocation, false));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static RenderType infraredArmorCutoutNoCull(ResourceLocation location) {
        return ARMOR_INFRARED_RENDER_TYPE.apply(location);
    }

    private static CompositeRenderType createInfraredArmorCutoutNoCull(String name, ResourceLocation id, boolean equalDepthTest) {
        CompositeState compositestate = RenderType.CompositeState.builder()
                .setShaderState(INFRARED_ARMOR_SHADER_SHARD)
                .setTextureState(new RenderStateShard.TextureStateShard(id, false, false))
                .setTransparencyState(RenderType.NO_TRANSPARENCY)
                .setCullState(RenderType.NO_CULL)
                .setLightmapState(RenderType.LIGHTMAP)
                .setOverlayState(RenderType.OVERLAY)
                .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING)
                .setDepthTestState(RenderType.NO_DEPTH_TEST)
                .createCompositeState(true);

        return new CompositeRenderType(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, compositestate);
    }

    @OnlyIn(Dist.CLIENT)
    static final class CompositeRenderType extends RenderType {
        static final BiFunction<ResourceLocation, CullStateShard, RenderType> OUTLINE = Util.memoize(
                (texture, cull) -> RenderType.create("outline", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536,
                        RenderType.CompositeState.builder()
                                .setShaderState(RENDERTYPE_OUTLINE_SHADER)
                                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                                .setCullState(cull)
                                .setDepthTestState(NO_DEPTH_TEST)
                                .setOutputState(OUTLINE_TARGET)
                                .createCompositeState(RenderType.OutlineProperty.IS_OUTLINE)
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
            IEmptyTextureStateAccessor textureAccessor = (IEmptyTextureStateAccessor) (Object) accessor.getTextureState();
            Optional<ResourceLocation> textureOpt = textureAccessor.callCutoutTexture();

            this.outline = accessor.getOutlineProperty() == RenderType.OutlineProperty.AFFECTS_OUTLINE
                    ? textureOpt.map(texture -> (RenderType) OUTLINE.apply(texture, accessor.getCullState()))
                    : Optional.empty();

            this.isOutline = accessor.getOutlineProperty() == RenderType.OutlineProperty.IS_OUTLINE;
        }

        public Optional<RenderType> outline() {
            return this.outline;
        }

        public boolean isOutline() {
            return this.isOutline;
        }

        protected final CompositeState state() {
            return this.state;
        }

        @Override
        public String toString() {
            return "RenderType[" + this.name + ":" + String.valueOf(this.state) + "]";
        }
    }
}
