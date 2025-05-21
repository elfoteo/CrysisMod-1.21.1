package com.elfoteo.tutorialmod.skill;

import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.network.custom.skills.ResetSkillsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.UnlockSkillPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

public class SkillData {

    private static Player getCurrentPlayer() {
        return Minecraft.getInstance().player;
    }

    public static Map<Skill, SkillState> getAllStates(Player player) {
        return player.getData(ModAttachments.ALL_SKILLS);
    }

    public static Map<Skill, SkillState> getAllStates() {
        return getAllStates(getCurrentPlayer());
    }

    private static SkillState getState(Player player, Skill skill) {
        return getAllStates(player).get(skill);
    }

    private static SkillState getState(Skill skill) {
        return getState(getCurrentPlayer(), skill);
    }

    public static boolean isUnlocked(Skill skill, Player player) {
        SkillState state = getState(player, skill);
        return state != null && state.isUnlocked();
    }

    public static boolean isUnlocked(Skill skill) {
        return isUnlocked(skill, getCurrentPlayer());
    }

    public static boolean isAvailable(Skill skill, Player player) {
        if (isUnlocked(skill, player)) return false;

        Skill[] parents = skill.getParents();
        if (parents.length == 0) return true;

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

    public static boolean unlock(Skill skill, Player player) {
        SkillState state = getState(player, skill);

        if (state == null) return false;

        if (!state.isUnlocked() && isAvailable(skill, player)) {
            state.setUnlocked(true);
            player.setData(ModAttachments.ALL_SKILLS, getAllStates(player));

            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new UnlockSkillPacket(skill, UnlockSkillPacket.Success.SUCCESS));
            }
            return true;
        } else {
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new UnlockSkillPacket(skill, UnlockSkillPacket.Success.FAILURE));
            }
            return false;
        }
    }

    public static boolean unlock(Skill skill) {
        return unlock(skill, getCurrentPlayer());
    }

    public static void reset(Player player) {
        Map<Skill, SkillState> states = getAllStates(player);
        for (SkillState state : states.values()) {
            state.setUnlocked(false);
        }
        player.setData(ModAttachments.ALL_SKILLS, states);

        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new ResetSkillsPacket());
        }
    }

    public static void reset() {
        reset(getCurrentPlayer());
    }

    public static int count(Player player) {
        return (int) getAllStates(player).values().stream().filter(SkillState::isUnlocked).count();
    }

    public static int count() {
        return count(getCurrentPlayer());
    }
}
