
package com.elfoteo.crysis.flag;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Bundles together:
 *   - the BlockPos of a flag
 *   - an optional display name (empty = no custom name)
 *   - the current owner (Team)
 *
 * Serializes to NBT as:
 *   {
 *     "x": int,
 *     "y": int,
 *     "z": int,
 *     "name": String,
 *     "team": String
 *   }
 */
public class FlagInfo {
    private final BlockPos pos;
    private String name;    // "" if no custom name
    private Team owner;

    public FlagInfo(BlockPos pos, String name, Team owner) {
        this.pos = pos;
        this.name = (name == null ? "" : name);
        this.owner = (owner == null ? Team.NONE : owner);
    }

    public BlockPos getPos() {
        return pos;
    }

    public String getName() {
        return name;
    }

    public Team getOwner() {
        return owner;
    }

    public void setName(String newName) {
        this.name = (newName == null ? "" : newName);
    }

    public void setOwner(Team newOwner) {
        this.owner = (newOwner == null ? Team.NONE : newOwner);
    }

    /** Write this FlagInfo into a CompoundTag. */
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("team", owner.name());
        return tag;
    }

    /** Read a FlagInfo from a CompoundTag. Returns null on any parse failure. */
    @Nullable
    public static FlagInfo fromTag(CompoundTag tag, BlockPos pos) {
        try {
            // Coordinates are read externally when constructing map
            String name = tag.getString("name");
            String teamStr = tag.getString("team");
            Team owner = Team.NONE;
            try {
                owner = Team.valueOf(teamStr);
            } catch (IllegalArgumentException ignored) {
                // leave as NONE if invalid
            }

            // Pos is set in capturing code
            return new FlagInfo(pos, name, owner);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Two FlagInfo objects are “equal” if they refer to the same BlockPos.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlagInfo)) return false;
        FlagInfo other = (FlagInfo) o;
        return Objects.equals(this.pos, other.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pos);
    }

    @Override
    public String toString() {
        return "FlagInfo{" +
                "pos=" + pos +
                ", name='" + name + '\'' +
                ", owner=" + owner +
                '}';
    }
}
