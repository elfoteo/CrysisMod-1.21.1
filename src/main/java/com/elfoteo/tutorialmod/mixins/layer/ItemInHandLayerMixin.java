package com.elfoteo.tutorialmod.mixins.layer;

import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.InfraredShader;
import com.elfoteo.tutorialmod.util.ItemRenderState;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.MatrixUtil;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(ItemRenderer.class)
public abstract class ItemInHandLayerMixin {
    @Shadow public abstract void renderModelLists(BakedModel model, ItemStack stack, int combinedLight, int combinedOverlay, PoseStack poseStack, VertexConsumer buffer);

    @Shadow @Final private ItemModelShaper itemModelShaper;

    @Shadow @Final private static ModelResourceLocation TRIDENT_MODEL;

    @Shadow @Final private static ModelResourceLocation SPYGLASS_MODEL;

    @Shadow
    private static boolean hasAnimatedTexture(ItemStack stack) {
        return false;
    }

    @Shadow
    public static VertexConsumer getCompassFoilBuffer(MultiBufferSource bufferSource, RenderType renderType, PoseStack.Pose pose) {
        return null;
    }

    @Shadow
    public static VertexConsumer getFoilBuffer(MultiBufferSource bufferSource, RenderType renderType, boolean isItem, boolean glint) {
        return null;
    }

    @Shadow
    public static VertexConsumer getFoilBufferDirect(MultiBufferSource bufferSource, RenderType renderType, boolean noEntity, boolean withGlint) {
        return null;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void render(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay, BakedModel p_model, CallbackInfo ci) {
        if (!itemStack.isEmpty()) {
            poseStack.pushPose();
            boolean flag = displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.GROUND || displayContext == ItemDisplayContext.FIXED;
            if (flag) {
                if (itemStack.is(Items.TRIDENT)) {
                    p_model = this.itemModelShaper.getModelManager().getModel(TRIDENT_MODEL);
                } else if (itemStack.is(Items.SPYGLASS)) {
                    p_model = this.itemModelShaper.getModelManager().getModel(SPYGLASS_MODEL);
                }
            }

            p_model = ClientHooks.handleCameraTransforms(poseStack, p_model, displayContext, leftHand);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            if (!p_model.isCustomRenderer() && (!itemStack.is(Items.TRIDENT) || flag)) {
                boolean flag1;
                label79: {
                    if (displayContext != ItemDisplayContext.GUI && !displayContext.firstPerson()) {
                        Item model = itemStack.getItem();
                        if (model instanceof BlockItem) {
                            BlockItem blockitem = (BlockItem)model;
                            Block block = blockitem.getBlock();
                            flag1 = !(block instanceof HalfTransparentBlock) && !(block instanceof StainedGlassPaneBlock);
                            break label79;
                        }
                    }

                    flag1 = true;
                }

                for(BakedModel model : p_model.getRenderPasses(itemStack, flag1)) {
                    for(RenderType rendertype : model.getRenderTypes(itemStack, flag1)) {
                        if ((ItemRenderState.rendering3DItem || displayContext == ItemDisplayContext.GROUND) && Nanosuit.currentClientMode == SuitModes.VISOR.get()){
                            rendertype = InfraredShader.INFRARED_RENDER_TYPE;
                        }

                        VertexConsumer vertexconsumer;
                        if (hasAnimatedTexture(itemStack) && itemStack.hasFoil()) {
                            PoseStack.Pose posestack$pose = poseStack.last().copy();
                            if (displayContext == ItemDisplayContext.GUI) {
                                MatrixUtil.mulComponentWise(posestack$pose.pose(), 0.5F);
                            } else if (displayContext.firstPerson()) {
                                MatrixUtil.mulComponentWise(posestack$pose.pose(), 0.75F);
                            }

                            vertexconsumer = getCompassFoilBuffer(bufferSource, rendertype, posestack$pose);
                        } else if (flag1) {
                            vertexconsumer = getFoilBufferDirect(bufferSource, rendertype, true, itemStack.hasFoil());
                        } else {
                            vertexconsumer = getFoilBuffer(bufferSource, rendertype, true, itemStack.hasFoil());
                        }

                        this.renderModelLists(model, itemStack, combinedLight, combinedOverlay, poseStack, vertexconsumer);
                    }
                }
            } else {
                IClientItemExtensions.of(itemStack).getCustomRenderer().renderByItem(itemStack, displayContext, poseStack, bufferSource, combinedLight, combinedOverlay);
            }

            poseStack.popPose();
        }

        ci.cancel();
    }
}
