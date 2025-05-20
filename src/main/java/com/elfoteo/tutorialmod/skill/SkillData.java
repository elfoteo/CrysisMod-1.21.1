package com.elfoteo.tutorialmod.skill;

import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.network.custom.skills.ResetSkillsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.UnlockSkillPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/**
 * SkillData now works in terms of List<SkillState> (from ModAttachments.ALL_SKILLS).
 * Each SkillState bundles a Skill + boolean unlocked.
 */
public class SkillData {

    /** Helper: get the client‐side “current” Player instance (only valid on client threads!). */
    private static Player getCurrentPlayer() {
        return Minecraft.getInstance().player;
    }

    /**
     * Returns the entire List<SkillState> from the player's attachment.  Every SkillState
     * in that list corresponds to one enum constant from Skill.values().
     */
    public static List<SkillState> getAllStates(Player player) {
        return player.getData(ModAttachments.ALL_SKILLS);
    }

    /** Overload to use the client‐side player. */
    public static List<SkillState> getAllStates() {
        return getAllStates(getCurrentPlayer());
    }

    /**
     * Returns the SkillState object corresponding to exactly the given Skill, if present.
     * (It should always be present—because we prefilled ALL_SKILLS with every Skill during attachment initialization.)
     */
    private static Optional<SkillState> findState(Player player, Skill skill) {
        return getAllStates(player).stream()
                .filter(state -> state.getSkill() == skill)
                .findFirst();
    }

    /** Overload for client‐side: */
    private static Optional<SkillState> findState(Skill skill) {
        return findState(getCurrentPlayer(), skill);
    }

    /**
     * True if the given skill is marked “unlocked” in that player’s SkillState list.
     */
    public static boolean isUnlocked(Skill skill, Player player) {
        return findState(player, skill)
                .map(SkillState::isUnlocked)
                .orElse(false);
    }

    public static boolean isUnlocked(Skill skill) {
        return isUnlocked(skill, getCurrentPlayer());
    }

    /**
     * A skill is available if:
     *   1) it is not already unlocked, AND
     *   2) either it has no parents, or at least one parent is unlocked.
     */
    public static boolean isAvailable(Skill skill, Player player) {
        if (isUnlocked(skill, player)) return false;

        Skill[] parents = skill.getParents();
        if (parents.length == 0) {
            return true; // no prerequisites
        }
        // at least one parent must already be unlocked
        for (Skill parent : parents) {
            if (isUnlocked(parent, player)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAvailable(Skill skill) {
        return isAvailable(skill, getCurrentPlayer());
    }

    /**
     * Unlock exactly one Skill, if it’s currently available and not yet unlocked.
     * Returns true if we succeeded, false otherwise.
     *
     * After flipping “unlocked” to true, send the appropriate packet back to the client.
     * (We assume server‐side runs this code whenever a skill is purchased, etc.)
     */
    public static boolean unlock(Skill skill, Player player) {
        Optional<SkillState> maybeState = findState(player, skill);

        if (maybeState.isEmpty()) {
            // Should never happen, because ALL_SKILLS always contains every SkillState.
            return false;
        }

        SkillState state = maybeState.get();
        if (!state.isUnlocked() && isAvailable(skill, player)) {
            state.setUnlocked(true);
            // write-back to the attachment:
            player.setData(ModAttachments.ALL_SKILLS, getAllStates(player));

            // If on the server side, send a packet to synchronize to that one player:
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new UnlockSkillPacket(skill, UnlockSkillPacket.Success.SUCCESS));
            }
            return true;
        } else {
            // Already unlocked or not available:
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new UnlockSkillPacket(skill, UnlockSkillPacket.Success.FAILURE));
            }
            return false;
        }
    }

    public static boolean unlock(Skill skill) {
        return unlock(skill, getCurrentPlayer());
    }

    /**
     * “Reset” means mark all SkillState.unlocked = false.  We keep the same order/size in the List,
     * but flip every unlocked → false.  Then send a ResetSkillsPacket back to the client.
     */
    public static void reset(Player player) {
        List<SkillState> states = getAllStates(player);
        for (SkillState ss : states) {
            ss.setUnlocked(false);
        }
        player.setData(ModAttachments.ALL_SKILLS, states);

        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new ResetSkillsPacket());
        }
    }

    public static void reset() {
        reset(getCurrentPlayer());
    }

    /** Count how many Skills are currently unlocked for that player. */
    public static int count(Player player) {
        return (int) getAllStates(player).stream().filter(SkillState::isUnlocked).count();
    }

    public static int count() {
        return count(getCurrentPlayer());
    }
}
