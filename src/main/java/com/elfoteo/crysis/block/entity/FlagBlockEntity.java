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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class FlagBlockEntity extends BlockEntity {
    private Team owner = Team.NONE;
    private int scoreCooldown = 0;
    private int redWoolCount = 0;
    private int blueWoolCount = 0;
    private int redConcreteCount = 0;
    private int blueConcreteCount = 0;

    private static final int SCORE_INTERVAL = 200;
    private static final int PARTICLE_INTERVAL = 5;
    // HARDENING_INTERVAL = 1 so concrete spawns as quickly as wool
    private static final int HARDENING_INTERVAL = 1;

    private final List<BlockPos> shuffledOffsets;

    public FlagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLAG_BE.get(), pos, state);
        this.shuffledOffsets = makeShuffledDiskOffsets(this.worldPosition);
    }

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

    @Override
    public void onLoad() {
        if (!this.level.isClientSide) {
            if (this.level instanceof ServerLevel serverLevel) {
                recalcStateFromWorld(serverLevel);
            }
        }
    }

    private void recalcStateFromWorld(ServerLevel level) {
        BlockPos center = this.worldPosition;
        int totalOffsets = this.shuffledOffsets.size();

        // Reset counts
        this.redWoolCount = 0;
        this.blueWoolCount = 0;
        this.redConcreteCount = 0;
        this.blueConcreteCount = 0;

        // Scan all offsets to rebuild counts and replace missing blocks with gray wool
        for (BlockPos offset : this.shuffledOffsets) {
            BlockPos target = center.offset(offset);
            BlockState bs = level.getBlockState(target);
            if (bs.getBlock() == Blocks.LIGHT_GRAY_WOOL) {
                // correct state; do nothing
            } else if (bs.getBlock() == Blocks.RED_WOOL) {
                this.redWoolCount++;
            } else if (bs.getBlock() == Blocks.BLUE_WOOL) {
                this.blueWoolCount++;
            } else if (bs.getBlock() == Blocks.RED_CONCRETE) {
                this.redConcreteCount++;
            } else if (bs.getBlock() == Blocks.BLUE_CONCRETE) {
                this.blueConcreteCount++;
            } else {
                // Missing or invalid block: replace with gray wool immediately
                level.setBlock(target, Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
            }
        }

        // Determine owner based on full counts
        int totalRed = this.redWoolCount + this.redConcreteCount;
        int totalBlue = this.blueWoolCount + this.blueConcreteCount;
        if (totalRed == totalOffsets) {
            this.owner = Team.RED;
        } else if (totalBlue == totalOffsets) {
            this.owner = Team.BLUE;
        } else {
            this.owner = Team.NONE;
        }

        // Reset scoreCooldown if owner changed or newly set
        this.scoreCooldown = 0;
        this.setChanged();
    }

    public static <T extends BlockEntity> void serverTick(Level genericLevel, BlockPos pos, BlockState state, T be) {
        if (!(genericLevel instanceof ServerLevel level)) return;
        if (!(be instanceof FlagBlockEntity flagBE)) return;

        BlockPos center = pos;
        int totalOffsets = flagBE.shuffledOffsets.size();

        // 1) Disk Integrity Check: scan all offsets each tick to instantly respawn gray wool if missing
        for (BlockPos offset : flagBE.shuffledOffsets) {
            BlockPos target = center.offset(offset);
            BlockState bs = level.getBlockState(target);
            boolean isValid = bs.getBlock() == Blocks.LIGHT_GRAY_WOOL ||
                    bs.getBlock() == Blocks.RED_WOOL ||
                    bs.getBlock() == Blocks.BLUE_WOOL ||
                    bs.getBlock() == Blocks.RED_CONCRETE ||
                    bs.getBlock() == Blocks.BLUE_CONCRETE;
            if (!isValid) {
                level.setBlock(target, Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
                // Adjust counts if replacing team blocks
                if (bs.getBlock() == Blocks.RED_WOOL) flagBE.redWoolCount--;
                else if (bs.getBlock() == Blocks.BLUE_WOOL) flagBE.blueWoolCount--;
                else if (bs.getBlock() == Blocks.RED_CONCRETE) flagBE.redConcreteCount--;
                else if (bs.getBlock() == Blocks.BLUE_CONCRETE) flagBE.blueConcreteCount--;
            }
        }

        // 2) Count players above flag
        int redsAbove = 0, bluesAbove = 0;
        AABB box = new AABB(
                pos.getX() - 6,
                pos.getY() + 1,
                pos.getZ() - 6,
                pos.getX() + 7,
                level.getMaxBuildHeight(),
                pos.getZ() + 7
        );
        for (Player p : level.getEntitiesOfClass(Player.class, box)) {
            Vec3 feetPos = p.position();
            if (feetPos.y <= pos.getY()) continue;
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

        // 3) Determine capturing team
        Team current = Team.NONE;
        if (redsAbove > 0 && bluesAbove == 0) current = Team.RED;
        else if (bluesAbove > 0 && redsAbove == 0) current = Team.BLUE;

        // 4) Spawn particles (less frequent)
        if (level.getGameTime() % PARTICLE_INTERVAL == 0) {
            Random rand = new Random();
            int centerX = center.getX();
            int centerY = center.getY();
            int centerZ = center.getZ();
            if (current == Team.RED) {
                for (int i = 0; i < 12; i++) {
                    int dx = rand.nextInt(3) - 1;
                    int dz = rand.nextInt(3) - 1;
                    int randomY = rand.nextInt(120 - (centerY + 1) + 1) + (centerY + 1);
                    double x = centerX + 0.5 + dx;
                    double y = randomY + rand.nextDouble() * 0.2;
                    double z = centerZ + 0.5 + dz;
                    level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.0, 0.05, 0.0, 0.0);
                }
            } else if (current == Team.BLUE) {
                for (int i = 0; i < 12; i++) {
                    int dx = rand.nextInt(3) - 1;
                    int dz = rand.nextInt(3) - 1;
                    int randomY = rand.nextInt(120 - (centerY + 1) + 1) + (centerY + 1);
                    double x = centerX + 0.5 + dx;
                    double y = randomY + rand.nextDouble() * 0.2;
                    double z = centerZ + 0.5 + dz;
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0.0, 0.05, 0.0, 0.0);
                }
            } else if (redsAbove > 0 && bluesAbove > 0) {
                for (int i = 0; i < 6; i++) {
                    int dx = rand.nextInt(3) - 1;
                    int dz = rand.nextInt(3) - 1;
                    double x = centerX + 0.5 + dx;
                    double y = centerY + 1.2;
                    double z = centerZ + 0.5 + dz;
                    level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }

        // 5) Capture and Hardening Logic
        if (current != Team.NONE) {
            if (flagBE.owner == current) {
                // Hardening: convert wool to concrete every tick
                for (BlockPos offset : flagBE.shuffledOffsets) {
                    BlockPos target = center.offset(offset);
                    BlockState currentState = level.getBlockState(target);
                    if (current == Team.RED && currentState.getBlock() == Blocks.RED_WOOL) {
                        level.setBlock(target, Blocks.RED_CONCRETE.defaultBlockState(), 3);
                        flagBE.redWoolCount--;
                        flagBE.redConcreteCount++;
                        break;
                    } else if (current == Team.BLUE && currentState.getBlock() == Blocks.BLUE_WOOL) {
                        level.setBlock(target, Blocks.BLUE_CONCRETE.defaultBlockState(), 3);
                        flagBE.blueWoolCount--;
                        flagBE.blueConcreteCount++;
                        break;
                    }
                }
            } else {
                // Capturing: first convert any owner-team concrete back to owner wool before any capture
                if (flagBE.owner != Team.NONE) {
                    boolean foundConcrete = false;
                    for (BlockPos offset : flagBE.shuffledOffsets) {
                        BlockPos target = center.offset(offset);
                        BlockState currentState = level.getBlockState(target);
                        if (currentState.getBlock() == (flagBE.owner == Team.RED ? Blocks.RED_CONCRETE : Blocks.BLUE_CONCRETE)) {
                            // Convert owner concrete to owner wool
                            if (flagBE.owner == Team.RED) {
                                flagBE.redConcreteCount--;
                                flagBE.redWoolCount++;
                                level.setBlock(target, Blocks.RED_WOOL.defaultBlockState(), 3);
                            } else {
                                flagBE.blueConcreteCount--;
                                flagBE.blueWoolCount++;
                                level.setBlock(target, Blocks.BLUE_WOOL.defaultBlockState(), 3);
                            }
                            foundConcrete = true;
                            break;
                        }
                    }
                    if (foundConcrete) {
                        return;
                    }
                }

                // No owner concrete remains (or owner == NONE), proceed to convert gray wool or opponent wool
                var targetWool = (current == Team.RED ? Blocks.RED_WOOL : Blocks.BLUE_WOOL);

                for (BlockPos offset : flagBE.shuffledOffsets) {
                    BlockPos target = center.offset(offset);
                    BlockState currentState = level.getBlockState(target);

                    // 5a) If it’s gray wool, immediately convert to capturing team’s wool
                    if (currentState.getBlock() == Blocks.LIGHT_GRAY_WOOL) {
                        if (current == Team.RED) flagBE.redWoolCount++;
                        else flagBE.blueWoolCount++;
                        level.setBlock(target, targetWool.defaultBlockState(), 3);
                        break;
                    }

                    // 5b) If it’s the opponent’s wool, convert to capturing team’s wool
                    if (currentState.getBlock() == (current == Team.RED ? Blocks.BLUE_WOOL : Blocks.RED_WOOL)) {
                        if (current == Team.RED) {
                            flagBE.blueWoolCount--;
                            flagBE.redWoolCount++;
                        } else {
                            flagBE.redWoolCount--;
                            flagBE.blueWoolCount++;
                        }
                        level.setBlock(target, targetWool.defaultBlockState(), 3);
                        break;
                    }

                    // 5c) If it’s opponent’s concrete, immediately convert it to that team’s wool
                    if (currentState.getBlock() == (current == Team.RED ? Blocks.BLUE_CONCRETE : Blocks.RED_CONCRETE)) {
                        if (current == Team.RED) {
                            flagBE.blueConcreteCount--;
                            flagBE.blueWoolCount++;
                            level.setBlock(target, Blocks.BLUE_WOOL.defaultBlockState(), 3);
                        } else {
                            flagBE.redConcreteCount--;
                            flagBE.redWoolCount++;
                            level.setBlock(target, Blocks.RED_WOOL.defaultBlockState(), 3);
                        }
                        break;
                    }

                    // 5d) If it’s already the capturing team’s wool or concrete, skip
                    if (currentState.getBlock() == targetWool ||
                            currentState.getBlock() == (current == Team.RED ? Blocks.RED_CONCRETE : Blocks.BLUE_CONCRETE)) {
                        continue;
                    }
                }
            }
        }

        // 6) Update Owner as soon as one team “owns” all blocks
        int totalRed = flagBE.redWoolCount + flagBE.redConcreteCount;
        int totalBlue = flagBE.blueWoolCount + flagBE.blueConcreteCount;
        if (totalRed == totalOffsets) {
            flagBE.setOwner(level, Team.RED);
        } else if (totalBlue == totalOffsets) {
            flagBE.setOwner(level, Team.BLUE);
        } else {
            flagBE.setOwner(level, Team.NONE);
        }

        // 7) Scoring
        if (flagBE.owner != Team.NONE) {
            flagBE.scoreCooldown++;
            if (flagBE.scoreCooldown >= SCORE_INTERVAL) {
                CaptureTheFlagData data = CaptureTheFlagData.getOrCreate(level);
                if (flagBE.owner == Team.RED) data.incrementRedScore(1);
                else data.incrementBlueScore(1);
                flagBE.scoreCooldown = 0;
            }
        }
    }

    private void setOwner(ServerLevel level, Team newOwner) {
        if (this.owner != newOwner) {
            this.owner = newOwner;
            this.scoreCooldown = 0;
            this.setChanged();
            CaptureTheFlagData data = CaptureTheFlagData.getOrCreate(level);
            data.setFlagOwner(this.worldPosition, newOwner);
            level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public Team getOwner() {
        return owner;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("owner", owner.name());
        tag.putInt("scoreCooldown", scoreCooldown);
        tag.putInt("redWoolCount", redWoolCount);
        tag.putInt("blueWoolCount", blueWoolCount);
        tag.putInt("redConcreteCount", redConcreteCount);
        tag.putInt("blueConcreteCount", blueConcreteCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.owner = tag.contains("owner") ? Team.valueOf(tag.getString("owner")) : Team.NONE;
        this.scoreCooldown = tag.getInt("scoreCooldown");
        this.redWoolCount = tag.getInt("redWoolCount");
        this.blueWoolCount = tag.getInt("blueWoolCount");
        this.redConcreteCount = tag.getInt("redConcreteCount");
        this.blueConcreteCount = tag.getInt("blueConcreteCount");
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
