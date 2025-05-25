package com.elfoteo.tutorialmod.block.custom;

import com.elfoteo.tutorialmod.block.entity.CreativeVendingMachineBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class CreativeVendingMachineBlock extends BaseEntityBlock {
    public static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);
    public static final MapCodec<CreativeVendingMachineBlock> CODEC = simpleCodec(CreativeVendingMachineBlock::new);
    public CreativeVendingMachineBlock(Properties properties) {
        super(properties);
    }
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    /* BLOCK ENTITY */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new CreativeVendingMachineBlockEntity(blockPos, blockState);
    }
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof CreativeVendingMachineBlockEntity creativeVendingMachineBlockEntity) {
                creativeVendingMachineBlockEntity.drops();
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof CreativeVendingMachineBlockEntity creativeVendingMachineBlockEntity) {
            if (!level.isClientSide()) {
                if (player.isCrouching() && player.isCreative()) {
                    player.openMenu(new SimpleMenuProvider(creativeVendingMachineBlockEntity, Component.literal("Trade Station (edit mode)")), pos);
                } else {
                    player.openMenu(new SimpleMenuProvider(creativeVendingMachineBlockEntity, Component.literal("Trade Station")), pos);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.CONSUME;
    }
}
