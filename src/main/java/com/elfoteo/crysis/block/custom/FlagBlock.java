// src/main/java/com/elfoteo/crysis/block/custom/FlagBlock.java
package com.elfoteo.crysis.block.custom;

import com.elfoteo.crysis.block.entity.FlagBlockEntity;
import com.elfoteo.crysis.block.entity.ModBlockEntities;
import com.elfoteo.crysis.flag.CTFData;
import com.elfoteo.crysis.flag.Team;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Overrides placement so that, as soon as the flag is placed by a player:
 *   • If any non‐air, non‐“previously‐placed‐wool” block exists in the would‐be wool‐disk → prevent placement,
 *     notify the player, and remove the flag block immediately (no wool is placed).
 *   • Otherwise → fill radius=6 disk with LIGHT_GRAY_WOOL, then reset owner to NONE.
 *
 * Also overrides onRemove so that, when the flag is broken, the entire wool/stone circle is cleared.
 * Finally, registers the server‐side ticker for FlagBlockEntity.
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
     * Called when a player (or dispenser, etc.) actually places this block in the world.
     * Because we need to know “who placed it,” we override setPlacedBy(...) instead of onPlace(...).
     *
     * Steps:
     *   1) Scan radius=6 around pos. If any non‐air block is found (except wool/concrete that a previous flag may have left),
     *      immediately remove the newly placed flag and send a chat message to the placing player. Do NOT place any wool.
     *   2) Otherwise, fill the disk with LIGHT_GRAY_WOOL and reset this block’s owner to NONE.
     */
    @Override
    public void setPlacedBy(Level generalLevel,
                            BlockPos pos,
                            BlockState state,
                            @Nullable LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(generalLevel, pos, state, placer, stack);

        // We only care on the SERVER side, and we only want real players
        if (generalLevel.isClientSide || !(generalLevel instanceof ServerLevel level) || !(placer instanceof ServerPlayer player)) {
            return;
        }

        // 1) Check for any obstruction in the radius=6 disk (excluding the flag pos itself).
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (dx * dx + dz * dz <= 36) {
                    BlockPos checkPos = pos.offset(dx, 0, dz);
                    BlockState bs = level.getBlockState(checkPos);
                    Block block = bs.getBlock();

                    // If it is NOT air, AND NOT one of the “old disk blocks” that a prior flag might have left,
                    // then we consider it an obstruction.
                    boolean isPreviousFlagWoolOrConcrete =
                            block == Blocks.LIGHT_GRAY_WOOL
                                    || block == Blocks.RED_WOOL
                                    || block == Blocks.BLUE_WOOL
                                    || block == Blocks.RED_CONCRETE
                                    || block == Blocks.BLUE_CONCRETE;

                    if (!bs.isAir() && !isPreviousFlagWoolOrConcrete) {
                        // Obstruction found → notify player, drop flag (remove block), and do NOT place wool.
                        player.sendSystemMessage(
                                Component.literal("§cCannot place flag: blocks in the way!§r")
                        );

                        // Remove the block we just placed
                        onRemove(state, level, pos, state, false);
                        level.removeBlock(pos, false);
                        return;
                    }
                }
            }
        }

        // 2) No obstruction → fill the radius=6 disk with LIGHT_GRAY_WOOL
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

        // 3) Reset the owner on the newly placed BlockEntity to NONE
        CTFData.getOrCreate(level).setFlagOwner(pos, Team.NONE);
    }

    /**
     * Called when the block is removed (broken or replaced).
     * We clear out any wool/concrete that was placed around it (radius=6 disk).
     */
    @Override
    public void onRemove(BlockState state,
                         Level generalLevel,
                         BlockPos pos,
                         BlockState newState,
                         boolean isMoving) {
        // Only run on server side, and only if the block type actually changed
        if (!generalLevel.isClientSide && state.getBlock() != newState.getBlock() && generalLevel instanceof ServerLevel level) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (dx * dx + dz * dz <= 36) {
                        BlockPos target = pos.offset(dx, 0, dz);
                        Block block = level.getBlockState(target).getBlock();
                        if (block == Blocks.LIGHT_GRAY_WOOL
                                || block == Blocks.RED_WOOL
                                || block == Blocks.BLUE_WOOL
                                || block == Blocks.RED_CONCRETE
                                || block == Blocks.BLUE_CONCRETE) {
                            level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
                            CTFData.getOrCreate(level).removeFlag(pos);
                        }
                    }
                }
            }
        }
        super.onRemove(state, generalLevel, pos, newState, isMoving);
    }

    /**
     * Register the server‐side ticker so that FlagBlockEntity.serverTick(...) is invoked each tick.
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                  BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide
                ? null
                : createTickerHelper(type, ModBlockEntities.FLAG_BE.get(), FlagBlockEntity::serverTick);
    }
}
