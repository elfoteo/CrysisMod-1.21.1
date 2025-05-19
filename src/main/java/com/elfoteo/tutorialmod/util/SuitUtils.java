package com.elfoteo.tutorialmod.util;

import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.network.custom.ArmorInfoPacket;
import com.elfoteo.tutorialmod.item.*;
import com.elfoteo.tutorialmod.gui.NanosuitOverlay;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class SuitUtils {
    /**
     * Checks if player is wearing the full nanosuit set.
     */
    public static boolean isWearingFullNanosuit(Player player) {
        ItemStack head = player.getInventory().getArmor(3);
        ItemStack chest = player.getInventory().getArmor(2);
        ItemStack legs = player.getInventory().getArmor(1);
        ItemStack boots = player.getInventory().getArmor(0);
        return isMatching(head, ModItems.NANOSUIT_HELMET.get()) &&
                isMatching(chest, ModItems.NANOSUIT_CHESTPLATE.get()) &&
                isMatching(legs, ModItems.NANOSUIT_LEGGINGS.get()) &&
                isMatching(boots, ModItems.NANOSUIT_BOOTS.get());
    }

    private static boolean isMatching(ItemStack stack, Item expectedItem) {
        return !stack.isEmpty() && stack.getItem() == expectedItem;
    }

    /**
     * Attempts to drain energy from the player. Returns true if successful.
     */
    public static boolean tryDrainEnergy(Player player, float amount) {
        float energy = player.getData(ModAttachments.ENERGY);
        if (energy >= amount) {
            float newEnergy = energy - amount;
            player.setData(ModAttachments.ENERGY, newEnergy);

            ArmorInfoPacket packet = new ArmorInfoPacket(
                    newEnergy,
                    player.getData(ModAttachments.MAX_ENERGY),
                    player.getData(ModAttachments.MAX_ENERGY_REGEN),
                    player.getData(ModAttachments.SUIT_MODE));
            if (player.level().isClientSide()) {
                // We're on the client — inform the server
                PacketDistributor.sendToServer(packet);
            } else {
                // We're on the server
                PacketDistributor.sendToPlayer((ServerPlayer) player, packet);
            }

            return true;
        }
        NanosuitOverlay.startRedBlink();
        return false;
    }

    /**
     * Returns true if the player has nanosuit energy armor mode active.
     */
    public static boolean isArmorMode(Player player) {
        return player.getData(ModAttachments.SUIT_MODE) == SuitModes.ARMOR.get();
    }

    /**
     * Handles generic damage absorption in Armor mode.
     * Absorbs 80% of damage by draining energy at 4x the damage.
     * Returns the new damage value to apply.
     */
    public static float absorbDamage(Player player, float incomingDamage) {
        if (!isArmorMode(player) || !isWearingFullNanosuit(player)) {
            return incomingDamage;
        }
        float drainAmount = incomingDamage * 4f;
        if (tryDrainEnergy(player, drainAmount)) {
            // Absorb 80%, so only 20% passes through
            return incomingDamage * 0.2f;
        } else {
            // Not enough energy: no absorption
            return incomingDamage;
        }
    }

    /**
     * Handles fall damage absorption in Armor mode.
     * Absorbs 100% of fall damage by draining energy at 5x the fall damage.
     * Returns true if fall damage should be canceled.
     */
    public static boolean absorbFallDamage(Player player, float fallDamage) {
        if (!isArmorMode(player) || !isWearingFullNanosuit(player)) {
            return false;
        }
        float drainAmount = fallDamage * 1.5f; // TODO: Tweak with a exponential curve or smth
        if (tryDrainEnergy(player, drainAmount)) {
            return true; // cancel all fall damage
        }
        return false;
    }
}
