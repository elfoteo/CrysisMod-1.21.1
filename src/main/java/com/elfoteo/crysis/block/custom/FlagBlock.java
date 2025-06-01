// src/main/java/com/elfoteo/crysis/block/custom/FlagBlock.java
package com.elfoteo.crysis.block.custom;

import com.elfoteo.crysis.block.entity.FlagBlockEntity;
import com.elfoteo.crysis.block.entity.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Overrides onPlace so that, as soon as the flag is placed, the  radius=4 disk is filled with GRAY wool.
 * Also registers the serverTicker for FlagBlockEntity.
 */
public class FlagBlock extends BaseEntityBlock {
    public static final MapCodec<FlagBlock> CODEC = simpleCodec(FlagBlock::new);

    public FlagBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlagBlockEntity(pos, state);
    }

    /**
     * Called when the block is placed (or loaded). Immediately fill the radius = 6 disk
     * around pos with LIGHT_GRAY_WOOL.
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (dx * dx + dz * dz <= 36) {
                        level.setBlock(pos.offset(dx, 0, dz),
                                Blocks.LIGHT_GRAY_WOOL.defaultBlockState(),
                                3);
                    }
                }
            }
        }
    }

    /**
     * Register the server‐side ticker so that FlagBlockEntity.serverTick(...) is invoked each tick.
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? null
                : createTickerHelper(type, ModBlockEntities.FLAG_BE.get(), FlagBlockEntity::serverTick);
    }
}
