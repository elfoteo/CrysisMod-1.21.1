package com.elfoteo.tutorialmod.block.custom;

import com.elfoteo.tutorialmod.block.entity.CreativeVendingMachineBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.SimpleMenuProvider;
import org.jetbrains.annotations.Nullable;

public class CreativeVendingMachineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<CreativeVendingMachineBlock> CODEC = simpleCodec(CreativeVendingMachineBlock::new);

    // ─── Z‐AXIS SHAPE (NORTH/SOUTH) ─────────────────────────────────────────────────────────────────
    // “Z_AXIS_SHAPE” is built by or‐ing together all of your JSON “elements” exactly as they appear:
    private static final VoxelShape Z_AXIS_SHAPE = Shapes.or(
            // Element 1: full cube
            Block.box(0d, 0d, 0d, 16d, 16d, 16d),

            // Element 2: from [3,16,2] to [4,18,3]
            Block.box(3d, 16d, 2d, 4d, 18d, 3d),

            // Element 3: from [12,16,2] to [13,18,3]
            Block.box(12d, 16d, 2d, 13d, 18d, 3d),

            // Element 4: from [3,16,13] to [4,18,14]
            Block.box(3d, 16d, 13d, 4d, 18d, 14d),

            // Element 5: from [12,16,13] to [13,18,14]
            Block.box(12d, 16d, 13d, 13d, 18d, 14d),

            // Element 6: from [3,19,2] to [13,20,4]
            Block.box(3d, 19d, 2d, 13d, 20d, 4d),

            // Element 7: from [11,19,4] to [13,20,12]
            Block.box(11d, 19d, 4d, 13d, 20d, 12d),

            // Element 8: from [3,19,4] to [5,20,12]
            Block.box(3d, 19d, 4d, 5d, 20d, 12d),

            // Element 9: from [3,18,2] to [13,19,14]
            Block.box(3d, 18d, 2d, 13d, 19d, 14d),

            // Element 10: from [3,19,12] to [13,20,14]
            Block.box(3d, 19d, 12d, 13d, 20d, 14d)
    );

    // ─── X‐AXIS SHAPE (EAST/WEST) ─────────────────────────────────────────────────────────────────
    // To get the “X_AXIS” version, we simply swap X↔Z for each cuboid, exactly as Anvil does.
    private static final VoxelShape X_AXIS_SHAPE = Shapes.or(
            // Element 1 (swap 0–16 in X/Z is the same for a full cube)
            Block.box(0d, 0d, 0d, 16d, 16d, 16d),

            // Element 2: original [3,16,2]→[4,18,3] becomes [2,16,3]→[3,18,4]
            Block.box(2d, 16d, 3d, 3d, 18d, 4d),

            // Element 3: original [12,16,2]→[13,18,3] becomes [2,16,12]→[3,18,13]
            Block.box(2d, 16d, 12d, 3d, 18d, 13d),

            // Element 4: original [3,16,13]→[4,18,14] becomes [13,16,3]→[14,18,4]
            Block.box(13d, 16d, 3d, 14d, 18d, 4d),

            // Element 5: original [12,16,13]→[13,18,14] becomes [13,16,12]→[14,18,13]
            Block.box(13d, 16d, 12d, 14d, 18d, 13d),

            // Element 6: original [3,19,2]→[13,20,4] becomes [2,19,3]→[4,20,13]
            Block.box(2d, 19d, 3d, 4d, 20d, 13d),

            // Element 7: original [11,19,4]→[13,20,12] becomes [4,19,11]→[12,20,13]
            Block.box(4d, 19d, 11d, 12d, 20d, 13d),

            // Element 8: original [3,19,4]→[5,20,12] becomes [4,19,3]→[12,20,5]
            Block.box(4d, 19d, 3d, 12d, 20d, 5d),

            // Element 9: original [3,18,2]→[13,19,14] becomes [2,18,3]→[14,19,13]
            Block.box(2d, 18d, 3d, 14d, 19d, 13d),

            // Element 10: original [3,19,12]→[13,20,14] becomes [12,19,3]→[14,20,13]
            Block.box(12d, 19d, 3d, 14d, 20d, 13d)
    );

    public CreativeVendingMachineBlock(Properties properties) {
        super(properties);
        // Default facing = NORTH
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

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
            if (level.getBlockEntity(pos) instanceof CreativeVendingMachineBlockEntity entity) {
                entity.drops();
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof CreativeVendingMachineBlockEntity entity) {
            if (!level.isClientSide()) {
                if (player.isCrouching() && player.isCreative()) {
                    player.openMenu(new SimpleMenuProvider(entity, Component.literal("Trade Station (edit mode)")), pos);
                } else {
                    player.openMenu(new SimpleMenuProvider(entity, Component.literal("Trade Station")), pos);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.CONSUME;
    }

    // BlockPlacement/FACING logic
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Only horizontal facing (N, S, E, W)
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // Here is where we return X_AXIS_SHAPE if facing east/west, else Z_AXIS_SHAPE
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return (facing.getAxis() == Axis.X) ? X_AXIS_SHAPE : Z_AXIS_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
