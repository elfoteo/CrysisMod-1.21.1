package com.elfoteo.tutorialmod.network.handlers;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.network.custom.*;
import com.elfoteo.tutorialmod.network.custom.skills.GetAllSkillsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.ResetSkillsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.SkillPointsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.UnlockSkillPacket;
import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.skill.SkillState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

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
            player.setData(ModAttachments.SUIT_MODE, packet.suitMode());
        });
    }
    public static void handleSuitModePacket(SuitModePacket packet, IPayloadContext context) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        context.enqueueWork(() -> {
            player.setData(ModAttachments.SUIT_MODE, packet.suitMode());
        });
    }

    public static void handleGetUnlockedSkillsPacket(GetAllSkillsPacket packet, IPayloadContext context) {
        // We received this packet it means that we previously requested data or the server is updating us
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        context.enqueueWork(() -> {
            player.setData(ModAttachments.ALL_SKILLS, packet.allSkills());
        });
    }

    public static void handleGetResetSkillsPacket(ResetSkillsPacket packet, IPayloadContext context) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        context.enqueueWork(() -> {
            // Reset ALL_SKILLS to default locked list
            List<SkillState> defaultSkills = new ArrayList<>();
            for (Skill s : Skill.values()) {
                defaultSkills.add(new SkillState(s, false));
            }
            player.setData(ModAttachments.ALL_SKILLS, defaultSkills);

            // Reset available skill points to initial value
            player.setData(ModAttachments.AVAILABLE_SKILL_POINTS, ModAttachments.INITAL_SKILL_POINTS);

            // Reset max skill points to initial value
            player.setData(ModAttachments.MAX_SKILL_POINTS, ModAttachments.INITAL_SKILL_POINTS);
        });
    }

    public static void handleUnlockSkillPacket(UnlockSkillPacket packet, IPayloadContext context) {
        // We received this packet — the server approved/unlocked a skill for us
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        Skill skill = packet.skill();
        if (packet.success() == UnlockSkillPacket.Success.SUCCESS) {
            context.enqueueWork(() -> {
                List<SkillState> currentSkills = player.getData(ModAttachments.ALL_SKILLS);

                // Find and unlock the skill
                for (SkillState state : currentSkills) {
                    if (state.getSkill().equals(skill)) {
                        state.setUnlocked(true);
                        break;
                    }
                }

                // Set the updated data
                player.setData(ModAttachments.ALL_SKILLS, currentSkills);
            });
        }
    }

    public static void handleSkillPointsPacket(SkillPointsPacket packet, IPayloadContext context) {
        // We received this packet it means that the server has some new data for us so we update ours to match the server
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
}
