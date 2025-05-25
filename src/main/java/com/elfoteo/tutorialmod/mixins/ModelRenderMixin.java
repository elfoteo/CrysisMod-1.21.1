package com.elfoteo.tutorialmod.mixins;

import com.elfoteo.tutorialmod.mixins.accessors.ICompositeStateAccessor;
import com.elfoteo.tutorialmod.mixins.accessors.TextureStateShardAccessor;
import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.ICompositeRenderType;
import com.elfoteo.tutorialmod.util.InfraredShader;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@OnlyIn(Dist.CLIENT)
@Mixin(Model.class)
public abstract class ModelRenderMixin {

    // Inject into the 4-arg renderToBuffer method
    @Inject(method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
            at = @At("HEAD"), cancellable = true)
    private void onRenderToBuffer(PoseStack poseStack, VertexConsumer ignoredOriginalConsumer, int packedLight, int packedOverlay, CallbackInfo ci) {
        if (Nanosuit.currentClientMode != SuitModes.VISOR.get()) return;

        // Get `this` as a Model instance
        Model self = (Model) (Object) this;

        // Create our own buffer using RedShader
        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(new ByteBufferBuilder(16384));
        VertexConsumer customConsumer = buffer.getBuffer(InfraredShader.INFRARED_SOLID_RENDERTYPE);

        // Call the abstract version (you can only do this if you’re extending your own subclass or know the model instance)
        // Use a dummy color value (-1 means no color override)
        self.renderToBuffer(poseStack, customConsumer, packedLight, packedOverlay, -1);

        ci.cancel(); // Prevent default behavior
    }

    private static Optional<ResourceLocation> extractTexture(RenderType type) {
        if (type instanceof RenderType.CompositeRenderType composite) {
            RenderType.CompositeState state =
                    ((ICompositeRenderType) (Object) composite).getState();

            RenderStateShard.EmptyTextureStateShard texture =
                    ((ICompositeStateAccessor) (Object) state).getTextureState();

            if (texture instanceof RenderStateShard.TextureStateShard){
                return ((TextureStateShardAccessor) texture).callCutoutTexture();
            }

            // Use the texture state shard to get the actual texture
            return Optional.empty(); // Optional<ResourceLocation>
        }
        return Optional.empty();
    }
}
