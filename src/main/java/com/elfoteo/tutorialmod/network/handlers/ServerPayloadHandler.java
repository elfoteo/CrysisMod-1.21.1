package com.elfoteo.tutorialmod.network.handlers;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.network.custom.*;
import com.elfoteo.tutorialmod.network.custom.skills.GetAllSkillsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.ResetSkillsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.SkillPointsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.UnlockSkillPacket;
import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.skill.SkillState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class ServerPayloadHandler {
    public static void handleArmorInfoPacket(ArmorInfoPacket packet, IPayloadContext context) {
        // If a client sends to the server this packet means that the client is
        // requesting data.
        if (context.player() instanceof ServerPlayer player) {
            ArmorInfoPacket newData = new ArmorInfoPacket(
                    player.getData(ModAttachments.ENERGY),
                    player.getData(ModAttachments.MAX_ENERGY),
                    player.getData(ModAttachments.MAX_ENERGY_REGEN),
                    player.getData(ModAttachments.SUIT_MODE));
            PacketDistributor.sendToPlayer(player, newData);
        }
    }
    public static void handleSuitModePacket(SuitModePacket packet, IPayloadContext context) {
        // If a client sends to the server this packet means that the client is
        // requesting data or he has changed his mode.
        if (context.player() instanceof ServerPlayer player) {
            SuitModePacket newData = new SuitModePacket(
                    player.getId(),
                    packet.suitMode());
            player.setData(ModAttachments.SUIT_MODE, packet.suitMode());
            PacketDistributor.sendToAllPlayers(newData);
        }
    }

    public static void handleGetAllSkillsPacket(GetAllSkillsPacket packet, IPayloadContext context) {
        // We received this packet it means that the client requested data and wants to know his unlocked skills
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            PacketDistributor.sendToPlayer(player, new GetAllSkillsPacket(
                    player.getData(ModAttachments.ALL_SKILLS)
            ));
        });
    }

    public static void handleGetResetSkillsPacket(ResetSkillsPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();

        context.enqueueWork(() -> {
            // Reset ALL_SKILLS to default locked state (recreate default list)
            List<SkillState> defaultSkills = new ArrayList<>();
            for (Skill s : Skill.values()) {
                defaultSkills.add(new SkillState(s, false));
            }
            player.setData(ModAttachments.ALL_SKILLS, defaultSkills);

            // Reset available skill points to initial value
            player.setData(ModAttachments.AVAILABLE_SKILL_POINTS, ModAttachments.INITAL_SKILL_POINTS);

            // Reset max skill points to initial value
            player.setData(ModAttachments.MAX_SKILL_POINTS, ModAttachments.INITAL_SKILL_POINTS);

            // Notify client the reset is done
            PacketDistributor.sendToPlayer(player, new ResetSkillsPacket());
        });
    }

    public static void handleUnlockSkillPacket(UnlockSkillPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        Skill skill = packet.skill();

        context.enqueueWork(() -> {
            List<SkillState> currentSkills = player.getData(ModAttachments.ALL_SKILLS);
            int availablePoints = player.getData(ModAttachments.AVAILABLE_SKILL_POINTS);

            // Check if skill is already unlocked
            boolean alreadyUnlocked = currentSkills.stream()
                    .anyMatch(state -> state.getSkill().equals(skill) && state.isUnlocked());

            // Find the target SkillState
            SkillState targetState = currentSkills.stream()
                    .filter(state -> state.getSkill().equals(skill))
                    .findFirst()
                    .orElse(null);

            if (targetState != null && !alreadyUnlocked && availablePoints > 0) {
                // Unlock the skill
                targetState.setUnlocked(true);

                // Deduct one skill point
                player.setData(ModAttachments.AVAILABLE_SKILL_POINTS, availablePoints - 1);

                // Save updated skill states
                player.setData(ModAttachments.ALL_SKILLS, currentSkills);

                // Send success packet
                PacketDistributor.sendToPlayer(player, new UnlockSkillPacket(
                        skill,
                        UnlockSkillPacket.Success.SUCCESS
                ));
            } else {
                // Send failure packet
                PacketDistributor.sendToPlayer(player, new UnlockSkillPacket(
                        skill,
                        UnlockSkillPacket.Success.FAILURE
                ));
            }
            // In any case ensure that the client has the right skill point count
            PacketDistributor.sendToPlayer(player, new SkillPointsPacket(
                    player.getData(ModAttachments.AVAILABLE_SKILL_POINTS),
                    player.getData(ModAttachments.MAX_SKILL_POINTS)
            ));
        });
    }

    public static void handleSkillPointsPacket(SkillPointsPacket packet, IPayloadContext context) {
        // The client has requested the skill point data, so we send it to him
        ServerPlayer player = (ServerPlayer) context.player();

        context.enqueueWork(() -> {
            PacketDistributor.sendToPlayer(player, new SkillPointsPacket(
                    player.getData(ModAttachments.AVAILABLE_SKILL_POINTS),
                    player.getData(ModAttachments.MAX_SKILL_POINTS)
            ));
        });
    }
}
