package com.elfoteo.crysis.block.entity;

import com.elfoteo.crysis.flag.CaptureTheFlagData;
import com.elfoteo.crysis.flag.Team;
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
    private Team owner = Team.NONE;
    private int scoreCooldown = 0;
    private int redWoolCount = 0;
    private int blueWoolCount = 0;
    private int redConcreteCount = 0;
    private int blueConcreteCount = 0;

    private static final int SCORE_INTERVAL = 200;
    private static final int RADIUS = 6;
    private static final double RADIUS_SQUARED = RADIUS * RADIUS;

    private final List<BlockPos> shuffledOffsets;

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
        AABB box = new AABB(worldPosition.getX() - RADIUS, worldPosition.getY() + 1, worldPosition.getZ() - RADIUS,
                worldPosition.getX() + RADIUS + 1, level.getMaxBuildHeight(), worldPosition.getZ() + RADIUS + 1);
        for (Player p : level.getEntitiesOfClass(Player.class, box)) {
            Vec3 pos = p.position();
            if (pos.y <= worldPosition.getY()) continue;
            double dx = pos.x - (worldPosition.getX() + 0.5);
            double dz = pos.z - (worldPosition.getZ() + 0.5);
            if (dx * dx + dz * dz <= RADIUS_SQUARED && p.getTeam() != null) {
                String team = p.getTeam().getName().toLowerCase();
                if (team.equals("red")) counts.merge(Team.RED, 1, Integer::sum);
                else if (team.equals("blue")) counts.merge(Team.BLUE, 1, Integer::sum);
            }
        }
        return counts;
    }

    private Team determineCapturingTeam(Map<Team, Integer> counts) {
        int red = counts.get(Team.RED);
        int blue = counts.get(Team.BLUE);
        return (red > 0 && blue == 0) ? Team.RED : (blue > 0 && red == 0) ? Team.BLUE : Team.NONE;
    }

    private Team otherTeam(Team team) {
        return team == Team.RED ? Team.BLUE : Team.RED;
    }

    private void handleCapture(ServerLevel level, Team capturingTeam) {
        if (capturingTeam == Team.NONE) return;

        if (capturingTeam == owner) {
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
                    adjustCounts(bs, -1);  // Decrease otherConcrete count
                    adjustCounts(otherWool.defaultBlockState(), 1);  // Increase otherWool count
                    return;  // Only convert one block per tick
                }
            }

            // Step 2: Convert other team's wool to gray wool
            for (BlockPos offset : shuffledOffsets) {
                BlockPos pos = worldPosition.offset(offset);
                BlockState bs = level.getBlockState(pos);
                if (bs.is(otherWool)) {
                    level.setBlock(pos, Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
                    adjustCounts(bs, -1);  // Decrease otherWool count
                    return;
                }
            }

            // Step 3: Convert gray wool to capturing team's wool
            for (BlockPos offset : shuffledOffsets) {
                BlockPos pos = worldPosition.offset(offset);
                BlockState bs = level.getBlockState(pos);
                if (bs.is(Blocks.LIGHT_GRAY_WOOL)) {
                    level.setBlock(pos, capturingWool.defaultBlockState(), 3);
                    adjustCounts(capturingWool.defaultBlockState(), 1);  // Increase capturingWool count
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
        Team newOwner = (redTotal == total) ? Team.RED : (blueTotal == total) ? Team.BLUE : Team.NONE;
        setOwner(level, newOwner);
    }

    private void handleScoring(ServerLevel level) {
        if (owner != Team.NONE && ++scoreCooldown >= SCORE_INTERVAL) {
            CaptureTheFlagData.getOrCreate(level).incrementScore(owner, 1);
            scoreCooldown = 0;
        }
    }

    private void setOwner(ServerLevel level, Team newOwner) {
        if (owner != newOwner) {
            owner = newOwner;
            scoreCooldown = 0;
            setChanged();
            CaptureTheFlagData.getOrCreate(level).setFlagOwner(worldPosition, newOwner);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public Team getOwner() {
        return owner;
    }

    public Map<Team, Integer> getPlayersInArea() {
        return level instanceof ServerLevel serverLevel ? countPlayers(serverLevel) : new HashMap<>(Map.of(Team.RED, 0, Team.BLUE, 0));
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
        owner = tag.contains("owner") ? Team.valueOf(tag.getString("owner")) : Team.NONE;
        scoreCooldown = tag.getInt("scoreCooldown");
        redWoolCount = tag.getInt("redWoolCount");
        blueWoolCount = tag.getInt("blueWoolCount");
        redConcreteCount = tag.getInt("redConcreteCount");
        blueConcreteCount = tag.getInt("blueConcreteCount");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("owner", owner.name());
        tag.putInt("scoreCooldown", scoreCooldown);
        return tag;
    }
}