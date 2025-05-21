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
     * If energy is insufficient, absorb as much as possible.
     * Returns the new damage value to apply.
     */
    public static float absorbDamage(Player player, float incomingDamage) {
        if (!(isArmorMode(player) && isWearingFullNanosuit(player))) {
            return incomingDamage;
        }

        float energy = player.getData(ModAttachments.ENERGY);
        float maxAbsorbable = energy / 4f; // 1 damage costs 4 energy
        float absorbableDamage = Math.min(incomingDamage, maxAbsorbable);
        float energyToDrain = absorbableDamage * 4f;

        if (energyToDrain > 0f) {
            player.setData(ModAttachments.ENERGY, energy - energyToDrain);
        }

        float absorbed = absorbableDamage * 0.8f;
        return incomingDamage - absorbed;
    }

    private static float getAvailableEnergy(Player player) {
        return player.getData(ModAttachments.ENERGY);
    }

    /**
     * Attempts to absorb fall damage in Armor mode.
     * Drains energy at 1.5x the fall damage amount.
     * Returns the amount of remaining damage that wasn't absorbed.
     */
    public static float absorbFallDamage(Player player, float fallDamage) {
        if (!isArmorMode(player) || !isWearingFullNanosuit(player)) {
            return fallDamage;
        }

        float drainAmount = fallDamage * 1.5f;
        float availableEnergy = getAvailableEnergy(player); // You need this method
        float absorbableDamage = Math.min(fallDamage, availableEnergy / 1.5f);
        float energyToDrain = absorbableDamage * 1.5f;

        if (absorbableDamage > 0) {
            tryDrainEnergy(player, energyToDrain);
        }

        return fallDamage - absorbableDamage;
    }
}
