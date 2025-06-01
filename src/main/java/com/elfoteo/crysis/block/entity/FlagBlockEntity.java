package com.elfoteo.crysis.block.entity;

import com.elfoteo.crysis.flag.CaptureTheFlagData;
import com.elfoteo.crysis.flag.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * BE for a Domination‐style flag, radius = 6 capture disk.
 *
 * - On placement (first load), fills the radius = 6 disk with light‐gray wool.
 * - Capturing: a player must stand strictly above the flag (feet‐Y > flagY)
 *   within horizontal radius = 6. While uninterrupted, each tick colors exactly
 *   one random block in the disk → team wool. When all blocks in the disk are recolored,
 *   ownership flips and scoring begins.
 * - If capture is interrupted (zero or both teams present), progress simply pauses;
 *   already‐colored blocks remain and owner stays NONE until a full capture.
 * - Disk integrity: if any position in the disk is missing or replaced by a non‐wool block,
 *   that position is reset to light‐gray wool each tick before capture logic runs.
 * - Whenever a player is in capture range:
 *   • If only RED present: spawn upward‐moving flame particles at random disk positions.
 *   • If only BLUE present: spawn upward‐moving soul fire flame particles at random disk positions.
 *   • If both present: spawn explosion particles at random disk positions.
 * - The vertical dust beam has been removed; no dust particles are spawned.
 * - While the disk is not fully captured by either team, owner remains NONE.
 */
public class FlagBlockEntity extends BlockEntity {
    /** Current owner: NONE = neutral. */
    private Team owner = Team.NONE;

    /** The team currently making capture progress (NONE if none or paused). */
    private Team capturing = Team.NONE;

    /** How many ticks since last point was awarded. Only used once owned. */
    private int scoreCooldown = 0;

    private static final int SCORE_INTERVAL = 200;

    /**
     * All integer offsets (dx,0,dz) with dx² + dz² ≤ 36, excluding (0,0), shuffled
     * deterministically based on block position so that reloads preserve ordering.
     */
    private final List<BlockPos> shuffledOffsets;

    public FlagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLAG_BE.get(), pos, state);
        this.shuffledOffsets = makeShuffledDiskOffsets(this.worldPosition);
    }

    /** Build & shuffle the radius = 6 disk offsets (omitting the center) deterministically per block position. */
    private static List<BlockPos> makeShuffledDiskOffsets(BlockPos centerPos) {
        List<BlockPos> list = new ArrayList<>();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (dx * dx + dz * dz <= 36) {
                    list.add(new BlockPos(dx, 0, dz));
                }
            }
        }
        Random rand = new Random(centerPos.hashCode());
        Collections.shuffle(list, rand);
        return list;
    }

    /**
     * Called when the block entity is loaded into the world.
     * On first load (owner == NONE), fill the disk with light-gray wool.
     */
    @Override
    public void onLoad() {
        if (!this.level.isClientSide && this.owner == Team.NONE) {
            if (this.level instanceof ServerLevel serverLevel) {
                fillDisk(serverLevel);
            }
        }
    }

    /** Fill the capture disk with light-gray wool (initial state). */
    private void fillDisk(ServerLevel level) {
        BlockPos center = this.worldPosition;
        for (BlockPos offset : this.shuffledOffsets) {
            BlockPos target = center.offset(offset);
            level.setBlock(target, Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
        }
    }

    /**
     * Called every server tick (registered by FlagBlock.getTicker(...)).
     *
     * 1) Disk Integrity Check: for each offset in the disk, if the block is neither gray wool, red wool,
     *    nor blue wool, replace it with light-gray wool.
     * 2) Count RED vs. BLUE players whose feet‐Y > flagY and horizontal dist² ≤ 36.
     * 3) Determine current capturing team (RED, BLUE, or NONE).
     * 4) Spawn appropriate particles at random disk positions if any players are present:
     *    • Only RED → upward flame.
     *    • Only BLUE → upward soul fire flame.
     *    • Both → explosion.
     * 5) If exactly one team above AND owner ≠ that team:
     *    • If capturing just switched (capturing != current):
     *        – set capturing = current,
     *        – if owner was previously RED or BLUE, clear owner → NONE and update world data.
     *    • Count how many blocks are currently that team's wool.
     *    • If count < totalOffsets: recolor exactly one random offset that is not that team's wool.
     *    • If count == totalOffsets: flip owner → that team; scoreCooldown = 0; capturing = NONE.
     * 6) If zero or both teams present OR owner == current → capturing = NONE.
     * 7) If owner ≠ NONE → each tick scoreCooldown++; when ≥ SCORE_INTERVAL, award 1 point and reset.
     */
    public static <T extends BlockEntity> void serverTick(Level genericLevel, BlockPos pos,
                                                          BlockState state, T be) {
        if (!(genericLevel instanceof ServerLevel level)) return;
        if (!(be instanceof FlagBlockEntity flagBE)) return;

        BlockPos center = pos;
        int totalOffsets = flagBE.shuffledOffsets.size();

        // 1) Disk Integrity Check
        for (BlockPos offset : flagBE.shuffledOffsets) {
            BlockPos target = center.offset(offset);
            BlockState bs = level.getBlockState(target);
            boolean isGray = bs.getBlock() == Blocks.LIGHT_GRAY_WOOL;
            boolean isRed  = bs.getBlock() == Blocks.RED_WOOL;
            boolean isBlue = bs.getBlock() == Blocks.BLUE_WOOL;
            if (!isGray && !isRed && !isBlue) {
                level.setBlock(target, Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
            }
        }

        // 2) Count RED vs. BLUE players whose feet‐Y > flagY and horizontal dist² ≤ 36
        int redsAbove = 0, bluesAbove = 0;
        AABB box = new AABB(
                pos.getX() - 6, pos.getY() + 1, pos.getZ() - 6,
                pos.getX() + 7, level.getMaxBuildHeight(), pos.getZ() + 7
        );
        for (Player p : level.getEntitiesOfClass(Player.class, box)) {
            Vec3 feetPos = p.position();
            if (feetPos.y <= pos.getY()) continue; // feet must be strictly above flag Y
            double dx = feetPos.x - (pos.getX() + 0.5);
            double dz = feetPos.z - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz <= 36.0) {
                if (p.getTeam() != null) {
                    String t = p.getTeam().getName();
                    if (t.equalsIgnoreCase("red")) redsAbove++;
                    else if (t.equalsIgnoreCase("blue")) bluesAbove++;
                }
            }
        }

        // 3) Determine current capturing team
        Team current = Team.NONE;
        if (redsAbove > 0 && bluesAbove == 0) {
            current = Team.RED;
        } else if (bluesAbove > 0 && redsAbove == 0) {
            current = Team.BLUE;
        }

        // 4) Spawn particles at random disk positions
        Random rand = new Random();
        if (redsAbove > 0 && bluesAbove == 0) {
            // Only RED present: spawn 3 flame particles at random offsets
            for (int i = 0; i < 3; i++) {
                BlockPos offset = flagBE.shuffledOffsets.get(rand.nextInt(totalOffsets));
                BlockPos target = center.offset(offset);
                double x = target.getX() + 0.5;
                double y = target.getY() + 1.2; // slightly above wool
                double z = target.getZ() + 0.5;
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.1, 0.3, 0.1, 0.0);
            }
        } else if (bluesAbove > 0 && redsAbove == 0) {
            // Only BLUE present: spawn 3 soul fire flame particles at random offsets
            for (int i = 0; i < 3; i++) {
                BlockPos offset = flagBE.shuffledOffsets.get(rand.nextInt(totalOffsets));
                BlockPos target = center.offset(offset);
                double x = target.getX() + 0.5;
                double y = target.getY() + 1.2;
                double z = target.getZ() + 0.5;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0.1, 0.3, 0.1, 0.0);
            }
        } else if (redsAbove > 0 && bluesAbove > 0) {
            // Both present: spawn 2 explosion particles at random offsets
            for (int i = 0; i < 2; i++) {
                BlockPos offset = flagBE.shuffledOffsets.get(rand.nextInt(totalOffsets));
                BlockPos target = center.offset(offset);
                double x = target.getX() + 0.5;
                double y = target.getY() + 1.0;
                double z = target.getZ() + 0.5;
                level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // 5) Capture logic
        if (current != Team.NONE && flagBE.owner != current) {
            // If capturing just switched, reset capturing state
            if (flagBE.capturing != current) {
                flagBE.capturing = current;
                // If there was a previous owner (RED or BLUE), clear it → NONE
                if (flagBE.owner != Team.NONE) {
                    flagBE.owner = Team.NONE;
                    flagBE.setChanged();
                    CaptureTheFlagData data = CaptureTheFlagData.getOrCreate(level);
                    data.setFlagOwner(center, Team.NONE);
                    level.sendBlockUpdated(center, flagBE.getBlockState(), flagBE.getBlockState(), 3);
                }
            }

            // Count how many blocks already match the capturing team's wool
            int progress = 0;
            Block targetBlock = (current == Team.RED ? Blocks.RED_WOOL : Blocks.BLUE_WOOL);
            for (BlockPos offset : flagBE.shuffledOffsets) {
                BlockPos target = center.offset(offset);
                if (level.getBlockState(target).getBlock() == targetBlock) {
                    progress++;
                }
            }

            if (progress < totalOffsets) {
                // Find one offset whose block is not capturing wool and recolor it
                for (BlockPos offset : flagBE.shuffledOffsets) {
                    BlockPos target = center.offset(offset);
                    if (level.getBlockState(target).getBlock() != targetBlock) {
                        level.setBlock(target, targetBlock.defaultBlockState(), 3);
                        break;
                    }
                }
                // Recompute progress: if now equal, complete capture
                progress = 0;
                for (BlockPos offset : flagBE.shuffledOffsets) {
                    BlockPos target = center.offset(offset);
                    if (level.getBlockState(target).getBlock() == targetBlock) {
                        progress++;
                    }
                }
                if (progress >= totalOffsets) {
                    // Fully captured → flip owner
                    flagBE.setOwner(level, current);
                    flagBE.scoreCooldown = 0;
                    flagBE.capturing = Team.NONE;
                }
            }
        } else {
            // Zero or both teams present, or already owned by current → pause capturing
            flagBE.capturing = Team.NONE;
        }

        // 6) Scoring if owned
        if (flagBE.owner == Team.RED) {
            flagBE.scoreCooldown++;
            if (flagBE.scoreCooldown >= SCORE_INTERVAL) {
                CaptureTheFlagData data = CaptureTheFlagData.getOrCreate(level);
                data.incrementRedScore(1);
                flagBE.scoreCooldown = 0;
            }
        } else if (flagBE.owner == Team.BLUE) {
            flagBE.scoreCooldown++;
            if (flagBE.scoreCooldown >= SCORE_INTERVAL) {
                CaptureTheFlagData data = CaptureTheFlagData.getOrCreate(level);
                data.incrementBlueScore(1);
                flagBE.scoreCooldown = 0;
            }
        }
    }

    /** Flip owner, mark dirty, save to world data, notify clients. */
    private void setOwner(ServerLevel level, Team newOwner) {
        this.owner = newOwner;
        this.setChanged();
        CaptureTheFlagData data = CaptureTheFlagData.getOrCreate(level);
        data.setFlagOwner(this.worldPosition, newOwner);
        level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    public Team getOwner() {
        return owner;
    }

    public int getScoreCooldown() {
        return scoreCooldown;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("owner", owner.name());
        tag.putInt("scoreCooldown", scoreCooldown);
        // capturing not persisted (progress resets on reload)
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner")) {
            try {
                this.owner = Team.valueOf(tag.getString("owner"));
            } catch (IllegalArgumentException ex) {
                this.owner = Team.NONE;
            }
        } else {
            this.owner = Team.NONE;
        }
        this.scoreCooldown = tag.getInt("scoreCooldown");
        this.capturing = Team.NONE;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag nbt = super.getUpdateTag(registries);
        nbt.putString("owner", owner.name());
        nbt.putInt("scoreCooldown", scoreCooldown);
        return nbt;
    }
}
