package com.elfoteo.crysis.mixins.nanosuit.infrared;

import com.elfoteo.crysis.mixins.accessors.ICompositeStateAccessor;
import com.elfoteo.crysis.mixins.accessors.TextureStateShardAccessor;
import com.elfoteo.crysis.util.ICompositeRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
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
