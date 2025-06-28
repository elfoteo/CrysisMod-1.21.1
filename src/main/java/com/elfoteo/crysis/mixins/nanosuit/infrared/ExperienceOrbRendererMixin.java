package com.elfoteo.crysis.mixins.nanosuit.infrared;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ExperienceOrbRenderer;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
@Mixin(ExperienceOrbRenderer.class)
public abstract class ExperienceOrbRendererMixin extends EntityRenderer<ExperienceOrb> {
    @Shadow
    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, int red, int green, int blue, float u, float v, int packedLight) {
    }

    @Shadow @Final private static ResourceLocation EXPERIENCE_ORB_LOCATION;
    @Unique
    private static RenderType INFRARED_RENDER_TYPE;

    protected ExperienceOrbRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    // Inject into the getRenderType method (this looks correct)
    @Inject(method = "render(Lnet/minecraft/world/entity/ExperienceOrb;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void getRenderType(ExperienceOrb entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) == SuitModes.VISOR.get()){
            if (INFRARED_RENDER_TYPE == null){
                INFRARED_RENDER_TYPE = InfraredShader.infraredEntityGeneric(EXPERIENCE_ORB_LOCATION);
            }
            poseStack.pushPose();
            int i = entity.getIcon();
            float f = (float)(i % 4 * 16) / 64.0F;
            float f1 = (float)(i % 4 * 16 + 16) / 64.0F;
            float f2 = (float)(i / 4 * 16) / 64.0F;
            float f3 = (float)(i / 4 * 16 + 16) / 64.0F;
            float f8 = ((float)entity.tickCount + partialTicks) / 2.0F;
            int j = (int)((Mth.sin(f8 + 0.0F) + 1.0F) * 0.5F * 255.0F);
            int l = (int)((Mth.sin(f8 + 4.1887903F) + 1.0F) * 0.1F * 255.0F);
            poseStack.translate(0.0F, 0.1F, 0.0F);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.scale(0.3F, 0.3F, 0.3F);
            VertexConsumer vertexconsumer = buffer.getBuffer(INFRARED_RENDER_TYPE);
            PoseStack.Pose posestack$pose = poseStack.last();
            vertex(vertexconsumer, posestack$pose, -0.5F, -0.25F, j, 255, l, f, f3, packedLight);
            vertex(vertexconsumer, posestack$pose, 0.5F, -0.25F, j, 255, l, f1, f3, packedLight);
            vertex(vertexconsumer, posestack$pose, 0.5F, 0.75F, j, 255, l, f1, f2, packedLight);
            vertex(vertexconsumer, posestack$pose, -0.5F, 0.75F, j, 255, l, f, f2, packedLight);
            poseStack.popPose();
            ci.cancel();
        }
    }
}