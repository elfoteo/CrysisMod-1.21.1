// src/main/java/com/elfoteo/crysis/flag/CaptureTheFlagData.java
package com.elfoteo.crysis.flag;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * World‐saved data for Domination‐style capture‐the‐flag.
 *
 * - Tracks each flag’s owner (RED, BLUE, or NONE)
 * - Tracks two global scores (redScore, blueScore)
 */
public class CaptureTheFlagData extends SavedData {
    private final Map<String, Team> flagOwners = new HashMap<>();
    private int redScore = 0;
    private int blueScore = 0;

    public CaptureTheFlagData() {}

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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
                // skip
            }
        }
        return data;
    }

    /**
     * Call when a flag at `pos` flips from NONE → RED or NONE → BLUE (first capture).
     * Does *not* increment on subsequent captures by the same team.
     */
    public void setFlagOwner(BlockPos pos, Team newTeam) {
        String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        Team old = flagOwners.getOrDefault(key, Team.NONE);
        if (old == Team.NONE && (newTeam == Team.RED || newTeam == Team.BLUE)) {
            // first time someone captures → bump that team’s score by 1 immediately
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

    /**
     * Useful helper to load or create this data for a given level:
     */
    public static CaptureTheFlagData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(CaptureTheFlagData::create, CaptureTheFlagData::load),
                "ctf_data"
        );
    }
}
