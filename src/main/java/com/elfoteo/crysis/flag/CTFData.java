package com.elfoteo.crysis.flag;

import com.elfoteo.crysis.block.ModBlocks;
import com.elfoteo.crysis.network.custom.CTFDataPacket;
import com.elfoteo.crysis.CrysisMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * World?s saved data for Capture-the-Flag, plus a client-only in-memory copy.
 *
 * On the server, this class is loaded/saved via SavedData (the usual world-storage path).
 * Anytime a flag or score changes, we immediately send a CTFDataPacket to all players.
 *
 * On the client, a separate static instance is managed (never written to disk), which can be
 * updated via incoming CTFDataPacket. All consumers (client or server) may call getters like
 * getFlags(), getRedScore(), etc. To push a brand-new set of flags (e.g. from a ?SYNC ALL? packet),
 * simply call setAllFlags(...) on the client instance.
 */
public class CTFData extends SavedData {
    // ------------------------------------------------------------------------------------
    // Server-side fields (and also reused by the client-side in-memory instance):
    // ------------------------------------------------------------------------------------

    private final Map<BlockPos, FlagInfo> flags = new HashMap<>();
    private int redScore = 0;
    private int blueScore = 0;

    // ------------------------------------------------------------------------------------
    // Client-side ?singleton?:
    //   - on the client, we never persist to disk; instead, getOrCreateClient() will produce
    //     an in-memory instance. Whenever a full sync arrives, call setClientFlags(...).
    // ------------------------------------------------------------------------------------

    private static CTFData clientInstance = null;

    // ------------------------------------------------------------------------------------
    // Distinguish server vs client instances:
    // ------------------------------------------------------------------------------------
    private final boolean clientSide;

    /**
     * Regular server-side constructor (SavedData).
     */
    public CTFData() {
        super();
        this.clientSide = false;
    }

    /**
     * Private constructor for client-only instance, which should _not_ be saved to disk.
     * The boolean flag here indicates client-side.
     */
    private CTFData(boolean isClient) {
        super();
        this.clientSide = isClient;
    }

    // ------------------------------------------------------------------------------------
    // Client-side accessors:
    // ------------------------------------------------------------------------------------

    /**
     * Retrieves (or creates) the client-side in-memory instance. Never written to disk.
     */
    public static CTFData getOrCreateClient() {
        if (clientInstance == null) {
            clientInstance = new CTFData(true);
        }
        return clientInstance;
    }

    /**
     * Completely replaces the client-side flag-map (and scores) with these values.
     * Call this when you receive a ?sync all flags + scores? from the server.
     */
    public static void setClientFlags(Collection<FlagInfo> newFlags, int newRedScore, int newBlueScore) {
        CTFData clientData = getOrCreateClient();
        clientData.setAllFlags(newFlags);
        clientData.redScore = newRedScore;
        clientData.blueScore = newBlueScore;
        // No need to push updates from client
    }

    // ------------------------------------------------------------------------------------
    // Server-side load/save (SavedData) methods:
    // ------------------------------------------------------------------------------------

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // Write scores:
        tag.putInt("redScore", redScore);
        tag.putInt("blueScore", blueScore);

        // Write each FlagInfo into a ListTag:
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, FlagInfo> entry : flags.entrySet()) {
            BlockPos pos = entry.getKey();
            FlagInfo info = entry.getValue();

            // toTag() writes name + team; now add coordinates and re-put name/team
            CompoundTag infoTag = info.toTag();
            infoTag.putInt("x", pos.getX());
            infoTag.putInt("y", pos.getY());
            infoTag.putInt("z", pos.getZ());
            infoTag.putString("name", info.getName());
            infoTag.putString("owner", info.getOwner().name());

            list.add(infoTag);
        }
        tag.put("flagEntries", list);
        return tag;
    }

    /** Called by Minecraft when reading from disk. */
    public static CTFData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        CTFData data = new CTFData();
        data.redScore = tag.getInt("redScore");
        data.blueScore = tag.getInt("blueScore");

        ListTag list = tag.getList("flagEntries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            int x = entryTag.getInt("x");
            int y = entryTag.getInt("y");
            int z = entryTag.getInt("z");
            BlockPos pos = new BlockPos(x, y, z);

            FlagInfo info = FlagInfo.fromTag(entryTag, pos);
            if (info != null) {
                data.flags.put(pos, info);
            }
        }
        return data;
    }

    /** Factory for world?s DataStorage. */
    public static CTFData create() {
        return new CTFData();
    }

    // ------------------------------------------------------------------------------------
    // Server-side getter: fetch (or create) the world?s CTF data.
    // Always calls validateFlags(...) and returns the SavedData instance.
    // ------------------------------------------------------------------------------------

    public static CTFData getOrCreate(ServerLevel level) {
        CTFData data = level.getDataStorage().computeIfAbsent(
                new Factory<>(CTFData::create, CTFData::load),
                "ctf_data"
        );
        data.validateFlags(level);
        return data;
    }

    // ------------------------------------------------------------------------------------
    // Common logic: getters, setters, validation, etc. Works on both clientInstance and server-side.
    // ------------------------------------------------------------------------------------

    /**
     * Remove any entries whose BlockPos is invalid or no longer holds a flag block.
     * (Only called on server.)
     */
    private void validateFlags(ServerLevel level) {
        boolean removedAny = false;
        Iterator<Map.Entry<BlockPos, FlagInfo>> iterator = flags.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, FlagInfo> entry = iterator.next();
            BlockPos pos = entry.getKey();
            Block block = level.getBlockState(pos).getBlock();
            if (!isValidFlagBlock(block)) {
                iterator.remove();
                removedAny = true;
            }
        }
        if (removedAny) {
            this.setDirty();
            sendUpdateToClients();
        }
    }

    /** Remove flag at given pos. */
    public void removeFlag(BlockPos pos) {
        if (flags.remove(pos) != null) {
            this.setDirty();
            sendUpdateToClients();
        }
    }

    private static boolean isValidFlagBlock(Block block) {
        return block == ModBlocks.FLAG.get();
    }

    /** Completely replaces the internal map of flags. */
    public void setAllFlags(Collection<FlagInfo> newFlags) {
        flags.clear();
        for (FlagInfo info : newFlags) {
            flags.put(info.getPos(), info);
        }
        this.setDirty();
        sendUpdateToClients();
    }

    /** Returns an unmodifiable view of all flags. */
    public Collection<FlagInfo> getFlags() {
        return Collections.unmodifiableCollection(flags.values());
    }

    /**
     * Called by server when a particular flag flips (red/blue/NONE).
     * If no FlagInfo existed at pos, create one with a random ?UnnamedXX? name.
     * If old owner was NONE and new is RED/BLUE, increment that team?s score.
     * Otherwise, just set the owner and mark dirty.
     */
    public void setFlagOwner(BlockPos pos, Team newTeam) {
        FlagInfo info = flags.get(pos);
        if (info == null) {
            // First time we see this flag: assign a random ?Unnamed##? name
            String randomName = "Unnamed" + (new Random().nextInt() & 0x7F);
            info = new FlagInfo(pos, randomName, newTeam);
            if (newTeam == Team.RED) {
                redScore++;
            } else if (newTeam == Team.BLUE) {
                blueScore++;
            }
            flags.put(pos, info);
        } else {
            Team old = info.getOwner();
            if (old == Team.NONE && (newTeam == Team.RED || newTeam == Team.BLUE)) {
                if (newTeam == Team.RED) redScore++;
                else blueScore++;
            }
            info.setOwner(newTeam);
        }
        this.setDirty();
        sendUpdateToClients();
    }

    /** Returns the owner of the flag at `pos`, or NONE if not present. */
    public Team getFlagOwner(BlockPos pos) {
        FlagInfo info = flags.get(pos);
        return (info == null ? Team.NONE : info.getOwner());
    }

    /** Assign or change the display name of a flag. */
    public void setFlagName(BlockPos pos, String name) {
        FlagInfo info = flags.get(pos);
        if (info != null) {
            info.setName(name == null ? "" : name);
            this.setDirty();
            sendUpdateToClients();
        }
    }

    /** Gets the display name (or empty string) of the flag at pos. */
    public String getFlagName(BlockPos pos) {
        FlagInfo info = flags.get(pos);
        return (info == null ? "" : info.getName());
    }

    // ----------------------------------------
    // Score helpers:
    // ----------------------------------------

    public int getRedScore() {
        return redScore;
    }

    public int getBlueScore() {
        return blueScore;
    }

    public void setRedScore(int score) {
        this.redScore = score;
        this.setDirty();
        sendUpdateToClients();
    }

    public void setBlueScore(int score) {
        this.blueScore = score;
        this.setDirty();
        sendUpdateToClients();
    }

    public void incrementRedScore(int amount) {
        this.redScore += amount;
        this.setDirty();
        sendUpdateToClients();
    }

    public void incrementBlueScore(int amount) {
        this.blueScore += amount;
        this.setDirty();
        sendUpdateToClients();
    }

    public void resetScores() {
        this.redScore = 0;
        this.blueScore = 0;
        this.setDirty();
        sendUpdateToClients();
    }

    /** Convenience: add to whichever team?s score. */
    public void incrementScore(Team owner, int amount) {
        switch (owner) {
            case RED -> incrementRedScore(amount);
            case BLUE -> incrementBlueScore(amount);
            default -> { /* NONE = no-op */ }
        }
    }

    // ------------------------------------------------------------------------------------
    // Private helper: send a CTFDataPacket containing the full map + both scores to all clients
    // Only executed on the server side (clientSide==false).
    // ------------------------------------------------------------------------------------

    private void sendUpdateToClients() {
        if (clientSide) {
            return; // Do not send from client
        }
        List<FlagInfo> snapshot = new ArrayList<>(flags.values());
        CTFDataPacket packet = new CTFDataPacket(snapshot, redScore, blueScore);
        PacketDistributor.sendToAllPlayers(packet);
    }

    public void sendUpdateToClient(ServerPlayer player) {
        if (clientSide) {
            return; // Do not send from client
        }
        List<FlagInfo> snapshot = new ArrayList<>(flags.values());
        CTFDataPacket packet = new CTFDataPacket(snapshot, redScore, blueScore);
        PacketDistributor.sendToPlayer(player, packet);
    }
}