package com.elfoteo.tutorialmod.network.handlers;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.network.custom.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
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
}
