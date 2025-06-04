package com.elfoteo.crysis.network.handlers;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.flag.CTFData;
import com.elfoteo.crysis.flag.FlagInfo;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.network.custom.*;
import com.elfoteo.crysis.network.custom.skills.GetAllSkillsPacket;
import com.elfoteo.crysis.network.custom.skills.ResetSkillsPacket;
import com.elfoteo.crysis.network.custom.skills.SkillPointsPacket;
import com.elfoteo.crysis.network.custom.skills.UnlockSkillPacket;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillState;
import com.elfoteo.crysis.util.SuitModes;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.EnumMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ClientPayloadHandler {

    public static void handleArmorInfoPacket(ArmorInfoPacket packet, IPayloadContext context) {
        Player player = Minecraft.getInstance().player;
        if (player == null)
            return;
        context.enqueueWork(() -> {
            player.setData(ModAttachments.ENERGY, packet.energy());
            player.setData(ModAttachments.MAX_ENERGY, packet.maxEnergy());
            player.setData(ModAttachments.MAX_ENERGY_REGEN, packet.maxEnergyRegen());
            //player.setData(ModAttachments.SUIT_MODE, packet.suitMode());
            Nanosuit.setClientMode(packet.suitMode(), player, false);
        });
    }

    public static void handleSuitModePacket(SuitModePacket packet, IPayloadContext context) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        context.enqueueWork(() -> {
            if (Nanosuit.currentClientMode == SuitModes.VISOR.get() && packet.suitMode() != SuitModes.VISOR.get()) {
                Nanosuit.previousClientMode = Nanosuit.currentClientMode; // Remember what we switched to after visor ends
            }
            Nanosuit.setClientMode(packet.suitMode(), player, false);
        });
    }

    public static void handleGetUnlockedSkillsPacket(GetAllSkillsPacket packet, IPayloadContext context) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        context.enqueueWork(() -> {
            // Server sent us a fresh map of skill states
            player.setData(ModAttachments.ALL_SKILLS, packet.allSkills());
        });
    }

    public static void handleGetResetSkillsPacket(ResetSkillsPacket packet, IPayloadContext context) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        context.enqueueWork(() -> {
            // Reset ALL_SKILLS to default locked state
            Map<Skill, SkillState> defaultSkills = new EnumMap<>(Skill.class);
            for (Skill s : Skill.values()) {
                defaultSkills.put(s, new SkillState(s, false));
            }
            player.setData(ModAttachments.ALL_SKILLS, defaultSkills);

            // Reset available skill points to initial value
            player.setData(ModAttachments.AVAILABLE_SKILL_POINTS, ModAttachments.INITAL_SKILL_POINTS);

            // Reset max skill points to initial value
            player.setData(ModAttachments.MAX_SKILL_POINTS, ModAttachments.INITAL_SKILL_POINTS);
        });
    }

    public static void handleUnlockSkillPacket(UnlockSkillPacket packet, IPayloadContext context) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        Skill skill = packet.skill();
        if (packet.success() == UnlockSkillPacket.Success.SUCCESS) {
            context.enqueueWork(() -> {
                Map<Skill, SkillState> currentSkills = player.getData(ModAttachments.ALL_SKILLS);

                SkillState state = currentSkills.get(skill);
                if (state != null) {
                    state.setUnlocked(true);
                    player.setData(ModAttachments.ALL_SKILLS, currentSkills);
                }
            });
        }
    }

    public static void handleSkillPointsPacket(SkillPointsPacket packet, IPayloadContext context) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        int availablePoints = packet.availablePoints();
        int maxPoints = packet.maxPoints();
        context.enqueueWork(() -> {
            player.setData(ModAttachments.AVAILABLE_SKILL_POINTS, availablePoints);
            player.setData(ModAttachments.MAX_SKILL_POINTS, maxPoints);
        });
    }

    public static void handleAllFlagsPacket(CTFDataPacket packet, IPayloadContext context) {
        CTFData.setClientFlags(packet.flags(), packet.redScore(), packet.blueScore());
        System.out.println("New flag data received from the server");
        for (FlagInfo flag : packet.flags()){
            System.out.println(flag);
        }
    }
}
