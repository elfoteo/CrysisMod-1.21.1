package com.elfoteo.crysis.attachments;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.flag.CTFData;
import com.elfoteo.crysis.network.custom.ArmorInfoPacket;
import com.elfoteo.crysis.network.custom.skills.GetAllSkillsPacket;
import com.elfoteo.crysis.network.custom.skills.SkillPointsPacket;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillState;
import com.elfoteo.crysis.util.SuitModes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = CrysisMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AttachmentSyncing {

    @SubscribeEvent
    public static void playerCloneData(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            if (event.getOriginal().hasData(ModAttachments.ENERGY)) {
                int originalMaxEnergy = event.getOriginal().getData(ModAttachments.MAX_ENERGY);
                event.getEntity().setData(ModAttachments.ENERGY, (float) originalMaxEnergy / 2);
                event.getEntity().setData(ModAttachments.MAX_ENERGY, originalMaxEnergy);
            }

            if (event.getOriginal().hasData(ModAttachments.SUIT_MODE)) {
                event.getEntity().setData(ModAttachments.SUIT_MODE, SuitModes.ARMOR.get());
            }

            if (event.getOriginal().hasData(ModAttachments.ALL_SKILLS)) {
                Map<Skill, SkillState> originalStates = event.getOriginal().getData(ModAttachments.ALL_SKILLS);

                Map<Skill, SkillState> copiedStates = originalStates.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> new SkillState(entry.getKey(), entry.getValue().isUnlocked())
                        ));

                event.getEntity().setData(ModAttachments.ALL_SKILLS, copiedStates);
            }

            if (event.getOriginal().hasData(ModAttachments.AVAILABLE_SKILL_POINTS)) {
                int available = event.getOriginal().getData(ModAttachments.AVAILABLE_SKILL_POINTS);
                event.getEntity().setData(ModAttachments.AVAILABLE_SKILL_POINTS, available);
            }

            if (event.getOriginal().hasData(ModAttachments.MAX_SKILL_POINTS)) {
                int maxPoints = event.getOriginal().getData(ModAttachments.MAX_SKILL_POINTS);
                event.getEntity().setData(ModAttachments.MAX_SKILL_POINTS, maxPoints);
            }
        }
    }

    @SubscribeEvent
    public static void playerRespawn(ClientPlayerNetworkEvent.Clone event) {
        if (!event.getNewPlayer().level().isClientSide) return;

        event.getNewPlayer().setData(ModAttachments.ENERGY,
                event.getOldPlayer().getData(ModAttachments.ENERGY));
        event.getNewPlayer().setData(ModAttachments.MAX_ENERGY,
                event.getOldPlayer().getData(ModAttachments.MAX_ENERGY));
        event.getNewPlayer().setData(ModAttachments.MAX_ENERGY_REGEN,
                event.getOldPlayer().getData(ModAttachments.MAX_ENERGY_REGEN));

        event.getNewPlayer().setData(ModAttachments.SUIT_MODE, 0);

        if (event.getOldPlayer().hasData(ModAttachments.ALL_SKILLS)) {
            Map<Skill, SkillState> originalStates = event.getOldPlayer().getData(ModAttachments.ALL_SKILLS);

            Map<Skill, SkillState> copiedStates = originalStates.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> new SkillState(entry.getKey(), entry.getValue().isUnlocked())
                    ));

            event.getNewPlayer().setData(ModAttachments.ALL_SKILLS, copiedStates);
        }

        if (event.getOldPlayer().hasData(ModAttachments.AVAILABLE_SKILL_POINTS)) {
            int oldAvailable = event.getOldPlayer().getData(ModAttachments.AVAILABLE_SKILL_POINTS);
            event.getNewPlayer().setData(ModAttachments.AVAILABLE_SKILL_POINTS, oldAvailable);
        }

        if (event.getOldPlayer().hasData(ModAttachments.MAX_SKILL_POINTS)) {
            int oldMax = event.getOldPlayer().getData(ModAttachments.MAX_SKILL_POINTS);
            event.getNewPlayer().setData(ModAttachments.MAX_SKILL_POINTS, oldMax);
        }

        requestPlayerData();
    }

    @SubscribeEvent
    public static void playerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        requestPlayerData();
    }

    private static void requestPlayerData() {
        PacketDistributor.sendToServer(new ArmorInfoPacket(0, 0, 0, 0));
        PacketDistributor.sendToServer(new SkillPointsPacket(0, 0));
        PacketDistributor.sendToServer(new GetAllSkillsPacket(new HashMap<>())); // Now using SkillState
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // This runs server-side when a player logs into the world
        Player genericPlayer = event.getEntity();

        // Example: get server level and run logic
        if (genericPlayer instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            // Your server-side logic here
            CTFData.getOrCreate(level).sendUpdateToClient(player);
        }
    }
}
