package com.elfoteo.crysis.mixins;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BrightnessCombiner;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(ChestRenderer.class)
public abstract class ChestRendererMixin<T extends BlockEntity & LidBlockEntity>
        implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<T> {

    @Shadow protected abstract Material getMaterial(T blockEntity, ChestType chestType);

    @Shadow @Final private ModelPart doubleLeftLid;
    @Shadow @Final private ModelPart doubleLeftLock;
    @Shadow @Final private ModelPart doubleLeftBottom;
    @Shadow @Final private ModelPart doubleRightBottom;
    @Shadow @Final private ModelPart doubleRightLock;
    @Shadow @Final private ModelPart doubleRightLid;
    @Shadow @Final private ModelPart lid;
    @Shadow @Final private ModelPart lock;
    @Shadow @Final private ModelPart bottom;

    @Shadow protected abstract void render(
            PoseStack poseStack,
            VertexConsumer consumer,
            ModelPart lidPart,
            ModelPart lockPart,
            ModelPart bottomPart,
            float lidAngle,
            int packedLight,
            int packedOverlay
    );

    @Inject(
            method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void render(
            T blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            CallbackInfo ci
    ) {
        // Only override when the suit is in VISOR (infrared) mode:
        if (Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) != SuitModes.VISOR.get()) {
            return; // Do NOT cancel: vanilla ChestRenderer#render will run as usual.
        }

        Level level = blockEntity.getLevel();
        boolean hasLevel = (level != null);
        BlockState blockstate = hasLevel
                ? blockEntity.getBlockState()
                : Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);

        ChestType chestType = blockstate.hasProperty(ChestBlock.TYPE)
                ? blockstate.getValue(ChestBlock.TYPE)
                : ChestType.SINGLE;

        if (!(blockstate.getBlock() instanceof AbstractChestBlock<?> abstractChest)) {
            // Not a chest? Let vanilla proceed.
            return;
        }

        // Combine double‐chest openness/brightness exactly like vanilla:
        DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> neighborCombine;
        if (hasLevel) {
            neighborCombine = abstractChest.combine(blockstate, level, blockEntity.getBlockPos(), true);
        } else {
            neighborCombine = DoubleBlockCombiner.Combiner::acceptNone;
        }

        float lidOpenness = ((Float2FloatFunction) neighborCombine.apply(ChestBlock.opennessCombiner(blockEntity)))
                .get(partialTick);
        lidOpenness = 1.0F - lidOpenness;
        lidOpenness = 1.0F - lidOpenness * lidOpenness * lidOpenness;

        int brightness = ((Int2IntFunction) neighborCombine.apply(new BrightnessCombiner()))
                .applyAsInt(packedLight);

        // Apply vanilla’s rotation/translation:
        poseStack.pushPose();
        float yaw = ((Direction) blockstate.getValue(ChestBlock.FACING)).toYRot();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        // 1) Get the vanilla chest‐texture Material:
        Material material = this.getMaterial(blockEntity, chestType);

        // 2) Extract the ResourceLocation of the texture from the Material:
        //    (In Yarn mappings, Material#texture() returns ResourceLocation.)
        ResourceLocation chestTexRL = material.texture();

        // 3) Call your static method that takes a ResourceLocation:
        RenderType infraredRT = InfraredShader.infraredBlock(chestTexRL);

        // 4) Finally ask the bufferSource for that RenderType:
        VertexConsumer vc = bufferSource.getBuffer(infraredRT);

        // 5) Render the correct model‐parts (single or double chest) with our custom VC:
        boolean isDouble = (chestType != ChestType.SINGLE);
        if (isDouble) {
            if (chestType == ChestType.LEFT) {
                this.render(
                        poseStack, vc,
                        this.doubleLeftLid, this.doubleLeftLock, this.doubleLeftBottom,
                        lidOpenness, brightness, packedOverlay
                );
            } else { // ChestType.RIGHT
                this.render(
                        poseStack, vc,
                        this.doubleRightLid, this.doubleRightLock, this.doubleRightBottom,
                        lidOpenness, brightness, packedOverlay
                );
            }
        } else {
            this.render(
                    poseStack, vc,
                    this.lid, this.lock, this.bottom,
                    lidOpenness, brightness, packedOverlay
            );
        }

        poseStack.popPose();
        ci.cancel(); // Prevent vanilla ChestRenderer from drawing again
    }
}
