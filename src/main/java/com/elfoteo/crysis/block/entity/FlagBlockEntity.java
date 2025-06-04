package com.elfoteo.crysis.block.entity;

import com.elfoteo.crysis.flag.CTFData;
import com.elfoteo.crysis.flag.Team;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class FlagBlockEntity extends BlockEntity {
    private int scoreCooldown = 0;
    private int redWoolCount = 0;
    private int blueWoolCount = 0;
    private int redConcreteCount = 0;
    private int blueConcreteCount = 0;

    private static final int SCORE_INTERVAL = 200;
    private static final int RADIUS = 6;
    private static final double RADIUS_SQUARED = RADIUS * RADIUS;

    private final List<BlockPos> shuffledOffsets;

    public void reset(){
        scoreCooldown = 0;
        redWoolCount = 0;
        blueWoolCount = 0;
        redConcreteCount = 0;
        blueConcreteCount = 0;

        // Clear any wool/concrete around the flag (radius = 6, excluding the flag block itself)
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (dx * dx + dz * dz <= 36) {
                    BlockPos targetPos = getBlockPos().offset(dx, 0, dz);
                    Block surroundingBlock = level.getBlockState(targetPos).getBlock();

                    boolean isFlagRelatedBlock =
                            surroundingBlock == Blocks.LIGHT_GRAY_WOOL ||
                                    surroundingBlock == Blocks.RED_WOOL ||
                                    surroundingBlock == Blocks.BLUE_WOOL ||
                                    surroundingBlock == Blocks.RED_CONCRETE ||
                                    surroundingBlock == Blocks.BLUE_CONCRETE;

                    if (isFlagRelatedBlock) {
                        level.setBlock(targetPos, Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    public FlagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLAG_BE.get(), pos, state);
        this.shuffledOffsets = makeShuffledDiskOffsets(pos);
    }

    private static List<BlockPos> makeShuffledDiskOffsets(BlockPos centerPos) {
        List<BlockPos> list = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (dx * dx + dz * dz <= RADIUS_SQUARED) {
                    list.add(new BlockPos(dx, 0, dz));
                }
            }
        }
        Collections.shuffle(list, new Random(centerPos.hashCode()));
        return list;
    }

    @Override
    public void onLoad() {
        if (level instanceof ServerLevel serverLevel) {
            recalcStateFromWorld(serverLevel);
        }
    }

    private void recalcStateFromWorld(ServerLevel level) {
        redWoolCount = blueWoolCount = redConcreteCount = blueConcreteCount = 0;
        for (BlockPos offset : shuffledOffsets) {
            BlockState bs = level.getBlockState(worldPosition.offset(offset));
            if (bs.is(Blocks.RED_WOOL)) redWoolCount++;
            else if (bs.is(Blocks.BLUE_WOOL)) blueWoolCount++;
            else if (bs.is(Blocks.RED_CONCRETE)) redConcreteCount++;
            else if (bs.is(Blocks.BLUE_CONCRETE)) blueConcreteCount++;
            else if (!bs.is(Blocks.LIGHT_GRAY_WOOL)) {
                level.setBlock(worldPosition.offset(offset), Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
            }
        }
        updateOwner(level);
        scoreCooldown = 0;
        setChanged();
    }

    public static <T extends BlockEntity> void serverTick(Level level, BlockPos pos, BlockState state, T be) {
        if (!(level instanceof ServerLevel serverLevel) || !(be instanceof FlagBlockEntity flag)) return;

        flag.ensureDiskIntegrity(serverLevel);
        Map<Team, Integer> playerCounts = flag.countPlayers(serverLevel);
        Team capturingTeam = flag.determineCapturingTeam(playerCounts);
        flag.handleCapture(serverLevel, capturingTeam);
        flag.updateOwner(serverLevel);
        flag.handleScoring(serverLevel);
    }

    private void ensureDiskIntegrity(ServerLevel level) {
        for (BlockPos offset : shuffledOffsets) {
            BlockPos pos = worldPosition.offset(offset);
            BlockState bs = level.getBlockState(pos);
            if (!isValidBlock(bs)) {
                level.setBlock(pos, Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
                adjustCounts(bs, -1);
            }
        }
    }

    private boolean isValidBlock(BlockState bs) {
        Block b = bs.getBlock();
        return b == Blocks.LIGHT_GRAY_WOOL || b == Blocks.RED_WOOL || b == Blocks.BLUE_WOOL ||
                b == Blocks.RED_CONCRETE || b == Blocks.BLUE_CONCRETE;
    }

    private void adjustCounts(BlockState bs, int delta) {
        if (bs.is(Blocks.RED_WOOL)) redWoolCount += delta;
        else if (bs.is(Blocks.BLUE_WOOL)) blueWoolCount += delta;
        else if (bs.is(Blocks.RED_CONCRETE)) redConcreteCount += delta;
        else if (bs.is(Blocks.BLUE_CONCRETE)) blueConcreteCount += delta;
    }

    private Map<Team, Integer> countPlayers(ServerLevel level) {
        Map<Team, Integer> counts = new HashMap<>(Map.of(Team.RED, 0, Team.BLUE, 0));
        AABB box = new AABB(
                worldPosition.getX() - RADIUS, worldPosition.getY() + 1, worldPosition.getZ() - RADIUS,
                worldPosition.getX() + RADIUS + 1, level.getMaxBuildHeight(), worldPosition.getZ() + RADIUS + 1
        );
        for (Player p : level.getEntitiesOfClass(Player.class, box)) {
            Vec3 pos = p.position();
            if (pos.y <= worldPosition.getY()) continue;
            double dx = pos.x - (worldPosition.getX() + 0.5);
            double dz = pos.z - (worldPosition.getZ() + 0.5);
            if (dx * dx + dz * dz <= RADIUS_SQUARED && p.getTeam() != null) {
                String teamName = p.getTeam().getName().toLowerCase();
                if (teamName.equals("red")) counts.merge(Team.RED, 1, Integer::sum);
                else if (teamName.equals("blue")) counts.merge(Team.BLUE, 1, Integer::sum);
            }
        }
        return counts;
    }

    private Team determineCapturingTeam(Map<Team, Integer> counts) {
        int red = counts.get(Team.RED);
        int blue = counts.get(Team.BLUE);
        return (red > 0 && blue == 0) ? Team.RED
                : (blue > 0 && red == 0) ? Team.BLUE
                : Team.NONE;
    }

    private Team otherTeam(Team team) {
        return team == Team.RED ? Team.BLUE : Team.RED;
    }

    private void handleCapture(ServerLevel level, Team capturingTeam) {
        if (capturingTeam == Team.NONE) return;

        Team currentOwner = CTFData.getOrCreate(level).getFlagOwner(worldPosition);
        if (capturingTeam == currentOwner) {
            hardenWool(level, capturingTeam);
        } else {
            Team other = otherTeam(capturingTeam);
            Block otherConcrete = other == Team.RED ? Blocks.RED_CONCRETE : Blocks.BLUE_CONCRETE;
            Block otherWool = other == Team.RED ? Blocks.RED_WOOL : Blocks.BLUE_WOOL;
            Block capturingWool = capturingTeam == Team.RED ? Blocks.RED_WOOL : Blocks.BLUE_WOOL;

            // Step 1: Convert other team's concrete to other team's wool
            for (BlockPos offset : shuffledOffsets) {
                BlockPos pos = worldPosition.offset(offset);
                BlockState bs = level.getBlockState(pos);
                if (bs.is(otherConcrete)) {
                    level.setBlock(pos, otherWool.defaultBlockState(), 3);
                    adjustCounts(bs, -1);
                    adjustCounts(otherWool.defaultBlockState(), 1);
                    return;  // Only convert one block per tick
                }
            }

            // Step 2: Convert other team's wool to gray wool
            for (BlockPos offset : shuffledOffsets) {
                BlockPos pos = worldPosition.offset(offset);
                BlockState bs = level.getBlockState(pos);
                if (bs.is(otherWool)) {
                    level.setBlock(pos, Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
                    adjustCounts(bs, -1);
                    return;
                }
            }

            // Step 3: Convert gray wool to capturing team's wool
            for (BlockPos offset : shuffledOffsets) {
                BlockPos pos = worldPosition.offset(offset);
                BlockState bs = level.getBlockState(pos);
                if (bs.is(Blocks.LIGHT_GRAY_WOOL)) {
                    level.setBlock(pos, capturingWool.defaultBlockState(), 3);
                    adjustCounts(capturingWool.defaultBlockState(), 1);
                    return;
                }
            }
        }
    }

    private void hardenWool(ServerLevel level, Team team) {
        Block wool = team == Team.RED ? Blocks.RED_WOOL : Blocks.BLUE_WOOL;
        Block concrete = team == Team.RED ? Blocks.RED_CONCRETE : Blocks.BLUE_CONCRETE;
        for (BlockPos offset : shuffledOffsets) {
            BlockPos pos = worldPosition.offset(offset);
            if (level.getBlockState(pos).is(wool)) {
                level.setBlock(pos, concrete.defaultBlockState(), 3);
                adjustCounts(wool.defaultBlockState(), -1);
                adjustCounts(concrete.defaultBlockState(), 1);
                break;
            }
        }
    }

    private void updateOwner(ServerLevel level) {
        int total = shuffledOffsets.size();
        int redTotal = redWoolCount + redConcreteCount;
        int blueTotal = blueWoolCount + blueConcreteCount;
        Team newOwner = (redTotal == total) ? Team.RED
                : (blueTotal == total) ? Team.BLUE
                : Team.NONE;

        Team currentOwner = CTFData.getOrCreate(level).getFlagOwner(worldPosition);
        if (currentOwner != newOwner) {
            // Reset cooldown and persist the new owner
            scoreCooldown = 0;
            setChanged();
            CTFData.getOrCreate(level).setFlagOwner(worldPosition, newOwner);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void handleScoring(ServerLevel level) {
        Team owner = CTFData.getOrCreate(level).getFlagOwner(worldPosition);
        if (owner != Team.NONE && ++scoreCooldown >= SCORE_INTERVAL) {
            CTFData.getOrCreate(level).incrementScore(owner, 1);
            scoreCooldown = 0;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // No longer saving `owner`; only persist counts and cooldown
        tag.putInt("scoreCooldown", scoreCooldown);
        tag.putInt("redWoolCount", redWoolCount);
        tag.putInt("blueWoolCount", blueWoolCount);
        tag.putInt("redConcreteCount", redConcreteCount);
        tag.putInt("blueConcreteCount", blueConcreteCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        scoreCooldown = tag.getInt("scoreCooldown");
        redWoolCount = tag.getInt("redWoolCount");
        blueWoolCount = tag.getInt("blueWoolCount");
        redConcreteCount = tag.getInt("redConcreteCount");
        blueConcreteCount = tag.getInt("blueConcreteCount");
        // Owner is not loaded here; fetched on demand
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        // No owner in tag; clients can request it from CaptureTheFlagData if needed
        tag.putInt("scoreCooldown", scoreCooldown);
        return tag;
    }

    public Team getOwner() {
        return CTFData.getOrCreateClient().getFlagOwner(worldPosition);
    }

    public Map<Team, Integer> getPlayersInArea() {
        if (level instanceof ServerLevel serverLevel) {
            return countPlayers(serverLevel);
        }
        return new HashMap<>(Map.of(Team.RED, 0, Team.BLUE, 0));
    }
}
