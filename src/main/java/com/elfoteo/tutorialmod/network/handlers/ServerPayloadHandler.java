package com.elfoteo.tutorialmod.network.handlers;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.network.custom.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
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
}
