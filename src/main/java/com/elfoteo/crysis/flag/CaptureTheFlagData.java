package com.elfoteo.crysis.flag;

import com.elfoteo.crysis.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

/**
 * World?s saved data for Domination?style capture?the?flag.
 *
 * - Tracks each flag?s owner (RED, BLUE, or NONE)
 * - Tracks two global scores (redScore, blueScore)
 *
 * Now, upon loading from NBT, we verify that each stored flag position
 * actually corresponds to a valid flag block in the world. If the block at
 * a given position is missing or isn?t a flag, we drop that entry to avoid
 * retaining corrupt/invalid data.
 */
public class CaptureTheFlagData extends SavedData {
    public final Map<String, Team> flagOwners = new HashMap<>();
    private int redScore = 0;
    private int blueScore = 0;

    public CaptureTheFlagData() {}

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("redScore", redScore);
        tag.putInt("blueScore", blueScore);

        ListTag list = new ListTag();
        for (Map.Entry<String, Team> e : flagOwners.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("pos", e.getKey());
            entry.putString("team", e.getValue().name());
            list.add(entry);
        }
        tag.put("flagOwners", list);
        return tag;
    }

    public static CaptureTheFlagData create() {
        return new CaptureTheFlagData();
    }

    public static CaptureTheFlagData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        CaptureTheFlagData data = new CaptureTheFlagData();
        data.redScore = tag.getInt("redScore");
        data.blueScore = tag.getInt("blueScore");

        ListTag list = tag.getList("flagOwners", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String key = entry.getString("pos");
            String teamStr = entry.getString("team");
            try {
                Team t = Team.valueOf(teamStr);
                data.flagOwners.put(key, t);
            } catch (IllegalArgumentException ignored) {
                // skip invalid team strings
            }
        }
        return data;
    }

    /**
     * Call when a flag at `pos` flips from NONE ? RED or NONE ? BLUE (first capture).
     * Does *not* increment on subsequent captures by the same team.
     */
    public void setFlagOwner(BlockPos pos, Team newTeam) {
        String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        Team old = flagOwners.getOrDefault(key, Team.NONE);
        if (old == Team.NONE && (newTeam == Team.RED || newTeam == Team.BLUE)) {
            // first time someone captures ? bump that team's score by 1 immediately
            if (newTeam == Team.RED) redScore++;
            else blueScore++;
        }
        flagOwners.put(key, newTeam);
        this.setDirty();
    }

    public Team getFlagOwner(BlockPos pos) {
        String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        return flagOwners.getOrDefault(key, Team.NONE);
    }

    public int getRedScore() {
        return redScore;
    }

    public int getBlueScore() {
        return blueScore;
    }

    public void incrementRedScore(int amount) {
        this.redScore += amount;
        this.setDirty();
    }

    public void incrementBlueScore(int amount) {
        this.blueScore += amount;
        this.setDirty();
    }

    public void setRedScore(int score) {
        this.redScore = score;
        this.setDirty();
    }

    public void setBlueScore(int score) {
        this.blueScore = score;
        this.setDirty();
    }

    public void resetScores() {
        this.redScore = 0;
        this.blueScore = 0;
        this.setDirty();
    }

    /**
     * Useful helper to load or create this data for a given level:
     * After reading from disk, immediately validate each saved flag position.
     * If the block at that position is not a valid flag, drop it from flagOwners.
     */
    public static CaptureTheFlagData getOrCreate(ServerLevel level) {
        CaptureTheFlagData data = level.getDataStorage().computeIfAbsent(
                new Factory<>(CaptureTheFlagData::create, CaptureTheFlagData::load),
                "ctf_data"
        );

        // Validate saved flag positions against actual world
        data.validateFlags(level);
        return data;
    }

    /**
     * Remove any entries whose BlockPos no longer corresponds to a valid flag block.
     * If any were removed, mark this SavedData as dirty so it will be re?saved.
     */
    private void validateFlags(ServerLevel level) {
        boolean removedAny = false;

        Iterator<Map.Entry<String, Team>> iterator = flagOwners.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Team> entry = iterator.next();
            String key = entry.getKey(); // stored as "x,y,z"
            BlockPos pos = parsePos(key);
            if (pos == null) {
                iterator.remove();
                removedAny = true;
                continue;
            }

            // Replace `YourModFlagBlock.INSTANCE` below with your actual flag?block reference.
            Block blockAtPos = level.getBlockState(pos).getBlock();
            if (!isValidFlagBlock(blockAtPos)) {
                // If no flag is present (or the block is corrupt), drop this entry
                iterator.remove();
                removedAny = true;
            }
        }

        if (removedAny) {
            this.setDirty();
        }
    }

    /**
     * Parse a "x,y,z" string into a BlockPos. Returns null if the format is invalid.
     */
    private static BlockPos parsePos(String key) {
        try {
            String[] parts = key.split(",");
            if (parts.length != 3) return null;
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return new BlockPos(x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Check whether the given Block corresponds to your mod's flag block.
     * Replace the placeholder with your actual flag?block reference.
     */
    private static boolean isValidFlagBlock(Block block) {
        return block == ModBlocks.FLAG.get();
    }

    public void incrementScore(Team owner, int i) {
        switch (owner) {
            case BLUE -> incrementBlueScore(i);
            case RED  -> incrementRedScore(i);
        }
    }
}
