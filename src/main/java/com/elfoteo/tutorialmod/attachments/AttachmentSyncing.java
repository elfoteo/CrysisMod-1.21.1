package com.elfoteo.tutorialmod.attachments;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.network.custom.ArmorInfoPacket;
import com.elfoteo.tutorialmod.util.SuitModes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AttachmentSyncing {
    // Sync the player data upon cloning (especially after death)
    @SubscribeEvent
    public static void playerCloneData(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            if (event.getOriginal().hasData(ModAttachments.ENERGY)) {
                int originalMaxEnergy = event.getOriginal().getData(ModAttachments.MAX_ENERGY);
                event.getEntity().setData(
                        ModAttachments.ENERGY,
                        (float) originalMaxEnergy / 2);
                // Assign max health value
                event.getEntity().setData(
                        ModAttachments.MAX_ENERGY,
                        originalMaxEnergy);
            }
            if (event.getOriginal().hasData(ModAttachments.SUIT_MODE)) {
                event.getEntity().setData(
                        ModAttachments.SUIT_MODE,
                        SuitModes.ARMOR.get());
            }
        }
    }

    // Sync the player's data when respawning
    @SubscribeEvent
    public static void playerRespawn(ClientPlayerNetworkEvent.Clone event) {
        if (!event.getNewPlayer().level().isClientSide) {
            return;
        }
        // Sync stats when the player respawns
        event.getNewPlayer().setData(ModAttachments.ENERGY,
                event.getOldPlayer().getData(ModAttachments.ENERGY));
        event.getNewPlayer().setData(ModAttachments.MAX_ENERGY,
                event.getOldPlayer().getData(ModAttachments.MAX_ENERGY));
        event.getNewPlayer().setData(ModAttachments.MAX_ENERGY_REGEN,
                event.getOldPlayer().getData(ModAttachments.MAX_ENERGY_REGEN));
        event.getNewPlayer().setData(ModAttachments.SUIT_MODE, 0);
        requestPlayerData();
    }

    // Sync the player's data when logging in
    @SubscribeEvent
    public static void playerRespawn(ClientPlayerNetworkEvent.LoggingIn event) {
        requestPlayerData();
    }

    private static void requestPlayerData() {
        // Request data from the server
        PacketDistributor.sendToServer(new ArmorInfoPacket(0, 0, 0, 0));
    }
}
