package com.elfoteo.crysis.mixins.nanosuit.infrared;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.PaintingTextureManager;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(PaintingRenderer.class)
public abstract class PaintingRendererMixin extends EntityRenderer<Painting> {
    @Shadow protected abstract void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float u, float v, float z, int normalX, int normalY, int normalZ, int packedLight);

    @Shadow protected abstract void renderPainting(PoseStack poseStack, VertexConsumer consumer, Painting painting, int width, int height, TextureAtlasSprite paintingSprite, TextureAtlasSprite backSprite);

    protected PaintingRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    // Inject into the getRenderType method (this looks correct)
    @Inject(method = "render(Lnet/minecraft/world/entity/decoration/Painting;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void renderPainting2(Painting entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) == SuitModes.VISOR.get()){
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
            PaintingVariant paintingvariant = entity.getVariant().value();
            VertexConsumer vertexconsumer = buffer.getBuffer(InfraredShader.infraredColdGeneric(this.getTextureLocation(entity)));
            PaintingTextureManager paintingtexturemanager = Minecraft.getInstance().getPaintingTextures();
            this.renderPainting(poseStack, vertexconsumer, entity, paintingvariant.width(), paintingvariant.height(), paintingtexturemanager.get(paintingvariant), paintingtexturemanager.getBackSprite());
            poseStack.popPose();
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            ci.cancel();
        }
    }
}