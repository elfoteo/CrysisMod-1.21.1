package com.elfoteo.tutorialmod.network.handlers;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.network.custom.*;
import com.elfoteo.tutorialmod.network.custom.skills.GetAllSkillsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.ResetSkillsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.SkillPointsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.UnlockSkillPacket;
import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.skill.SkillState;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.elfoteo.tutorialmod.util.SuitUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        if (context.player() instanceof ServerPlayer player) {
            int requestedMode = packet.suitMode();
            int currentMode = player.getData(ModAttachments.SUIT_MODE);

            // Only handle full nanosuit wearers
            if (!SuitUtils.isWearingFullNanosuit(player)) {
            player.setData(ModAttachments.SUIT_MODE, SuitModes.NOT_EQUIPPED.get());
                PacketDistributor.sendToPlayer(player,
                        new SuitModePacket(player.getId(), SuitModes.NOT_EQUIPPED.get()));
                return;
            };

            // If switching to CLOAK mode, validate conditions
            if (requestedMode == SuitModes.CLOAK.get()) {
                float energy = player.getData(ModAttachments.ENERGY);
                long now = System.currentTimeMillis();
                long blockedUntil = Nanosuit.cloakBreakTimestamps.getOrDefault(player.getUUID(), 0L);
                boolean cloakBlocked = now < blockedUntil;

                if (energy > 0f && currentMode != SuitModes.CLOAK.get() && !cloakBlocked) {
                    player.setData(ModAttachments.SUIT_MODE, SuitModes.CLOAK.get());
                    PacketDistributor.sendToPlayer(player,
                            new SuitModePacket(player.getId(), SuitModes.CLOAK.get()));
                }
                else {
                    PacketDistributor.sendToPlayer(player,
                            new SuitModePacket(player.getId(), SuitModes.ARMOR.get()));
                }
                // else: do not send packet, cloak activation denied
            } else {
                // All other modes (ARMOR, VISOR, etc.) switch instantly
                player.setData(ModAttachments.SUIT_MODE, requestedMode);
                PacketDistributor.sendToPlayer(player,
                        new SuitModePacket(player.getId(), requestedMode));
            }
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
            // Reset ALL_SKILLS to default locked state (recreate default map)
            Map<Skill, SkillState> defaultSkills = Arrays.stream(Skill.values())
                    .collect(Collectors.toMap(
                            skill -> skill,
                            skill -> new SkillState(skill, false)
                    ));

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
            Map<Skill, SkillState> currentSkills = player.getData(ModAttachments.ALL_SKILLS);
            int availablePoints = player.getData(ModAttachments.AVAILABLE_SKILL_POINTS);

            SkillState targetState = currentSkills.get(skill);

            boolean alreadyUnlocked = targetState != null && targetState.isUnlocked();

            if (targetState != null && !alreadyUnlocked && availablePoints > 0) {
                // Unlock the skill
                targetState.setUnlocked(true);

                // Deduct one skill point
                player.setData(ModAttachments.AVAILABLE_SKILL_POINTS, availablePoints - 1);

                // Save updated skill states
                player.setData(ModAttachments.ALL_SKILLS, currentSkills);

                // Send success packet
                PacketDistributor.sendToPlayer(player, new UnlockSkillPacket(skill, UnlockSkillPacket.Success.SUCCESS));
            } else {
                // Send failure packet
                PacketDistributor.sendToPlayer(player, new UnlockSkillPacket(skill, UnlockSkillPacket.Success.FAILURE));
            }

            // Sync skill points regardless of outcome
            PacketDistributor.sendToPlayer(player, new SkillPointsPacket(
                    player.getData(ModAttachments.AVAILABLE_SKILL_POINTS),
                    player.getData(ModAttachments.MAX_SKILL_POINTS)
            ));
        });
    }

    public static void handleSkillPointsPacket(SkillPointsPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();

        context.enqueueWork(() -> {
            PacketDistributor.sendToPlayer(player, new SkillPointsPacket(
                    player.getData(ModAttachments.AVAILABLE_SKILL_POINTS),
                    player.getData(ModAttachments.MAX_SKILL_POINTS)
            ));
        });
    }
}
