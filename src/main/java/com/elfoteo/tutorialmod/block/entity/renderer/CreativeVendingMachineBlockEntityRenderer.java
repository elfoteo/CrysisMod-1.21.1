package com.elfoteo.tutorialmod.block.entity.renderer;

import com.elfoteo.tutorialmod.block.entity.CreativeVendingMachineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public class CreativeVendingMachineBlockEntityRenderer implements BlockEntityRenderer<CreativeVendingMachineBlockEntity> {
    private static final int ITEM_CHANGE_TIME = 60;

    public CreativeVendingMachineBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CreativeVendingMachineBlockEntity blockEntity,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay)
    {
        ItemStackHandler handler = blockEntity.inventory;
        Level level = blockEntity.getLevel();
        if (level == null) return;

        // 1) Collect all non-empty output‐slot ItemStacks (indices 6..11)
        List<ItemStack> outputStacks = new ArrayList<>();
        for (int i = 6; i < 12; i++) {
            ItemStack out = handler.getStackInSlot(i);
            if (!out.isEmpty()) {
                outputStacks.add(out);
            }
        }
        if (outputStacks.isEmpty()) {
            return; // Nothing to render
        }

        // 2) Choose which output to display: one per second, cycling
        long gameTime = level.getGameTime();           // in ticks
        int idx = (int) ((gameTime / ITEM_CHANGE_TIME) % outputStacks.size());
        ItemStack toRender = outputStacks.get(idx);

        // 3) Render the chosen stack slightly higher (0.2 blocks above previous y)
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        poseStack.pushPose();
        // Original translation was (0.5, 1.15, 0.5). Add +0.2 to Y:
        poseStack.translate(0.5f, 1.35f, 0.5f);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(blockEntity.getRenderingRotation()));

        int light = getLightLevel(level, blockEntity.getBlockPos());
        itemRenderer.renderStatic(toRender,
                ItemDisplayContext.FIXED,
                light,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                level,
                1);

        poseStack.popPose();
    }

    private int getLightLevel(Level level, BlockPos pos) {
        int bLight = level.getBrightness(LightLayer.BLOCK, pos);
        int sLight = level.getBrightness(LightLayer.SKY, pos);
        return LightTexture.pack(bLight, sLight);
    }
}
