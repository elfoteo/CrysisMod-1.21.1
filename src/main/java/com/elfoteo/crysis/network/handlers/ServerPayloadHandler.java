package com.elfoteo.crysis.network.handlers;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.flag.CTFData;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.network.custom.*;
import com.elfoteo.crysis.network.custom.skills.GetAllSkillsPacket;
import com.elfoteo.crysis.network.custom.skills.ResetSkillsPacket;
import com.elfoteo.crysis.network.custom.skills.SkillPointsPacket;
import com.elfoteo.crysis.network.custom.skills.UnlockSkillPacket;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillState;
import com.elfoteo.crysis.util.SuitModes;
import com.elfoteo.crysis.util.SuitUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class ServerPayloadHandler {
    public static void handleArmorInfoPacket(ArmorInfoPacket packet, IPayloadContext context) {
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
            }

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
                } else {
                    PacketDistributor.sendToPlayer(player,
                            new SuitModePacket(player.getId(), SuitModes.ARMOR.get()));
                }
            } else {
                // All other modes (ARMOR, VISOR, etc.) switch instantly
                player.setData(ModAttachments.SUIT_MODE, requestedMode);
                PacketDistributor.sendToPlayer(player,
                        new SuitModePacket(player.getId(), requestedMode));
            }
        }
    }

    public static void handleGetAllSkillsPacket(GetAllSkillsPacket packet, IPayloadContext context) {
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
            // Reset ALL_SKILLS to default locked state
            Map<Skill, SkillState> defaultSkills = Arrays.stream(Skill.values())
                    .collect(Collectors.toMap(
                            skill -> skill,
                            skill -> new SkillState(skill, false)
                    ));

            player.setData(ModAttachments.ALL_SKILLS, defaultSkills);
            player.setData(ModAttachments.AVAILABLE_SKILL_POINTS, ModAttachments.INITAL_SKILL_POINTS);
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
            boolean alreadyUnlocked = (targetState != null && targetState.isUnlocked());

            if (targetState != null && !alreadyUnlocked && availablePoints > 0) {
                // Unlock the skill
                targetState.setUnlocked(true);

                // Deduct one skill point
                player.setData(ModAttachments.AVAILABLE_SKILL_POINTS, availablePoints - 1);

                // If this skill is ENHANCED_BATTERIES, bump MAX_ENERGY by 15%
                if (skill == Skill.ENHANCED_BATTERIES) {
                    float oldMax = player.getData(ModAttachments.MAX_ENERGY);
                    int newMax = (int) (oldMax * 1.15f); // +15%
                    player.setData(ModAttachments.MAX_ENERGY, newMax);

                    // Immediately send updated energy info back to the client:
                    ArmorInfoPacket armorInfo = new ArmorInfoPacket(
                            player.getData(ModAttachments.ENERGY),
                            newMax,
                            player.getData(ModAttachments.MAX_ENERGY_REGEN),
                            player.getData(ModAttachments.SUIT_MODE));
                    PacketDistributor.sendToPlayer(player, armorInfo);
                }

                // Save updated skill states
                player.setData(ModAttachments.ALL_SKILLS, currentSkills);

                // Send success packet
                PacketDistributor.sendToPlayer(player,
                        new UnlockSkillPacket(skill, UnlockSkillPacket.Success.SUCCESS));
            } else {
                // Send failure packet
                PacketDistributor.sendToPlayer(player,
                        new UnlockSkillPacket(skill, UnlockSkillPacket.Success.FAILURE));
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

    public static void handleAllFlagsPacket(CTFDataPacket ignored, IPayloadContext context) {
        // If we receive this packet from the client it means that he wants us to give him the CTFData that we have
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(context.player().level() instanceof ServerLevel level)) return;
        CTFData data = CTFData.getOrCreate(level);
        CTFDataPacket packet = new CTFDataPacket(
                data.getFlags().stream().toList(),
                data.getBlueScore(),
                data.getRedScore()
        );
        PacketDistributor.sendToPlayer(player, packet);
    }
}
