package com.elfoteo.crysis.util;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.network.custom.ArmorInfoPacket;
import com.elfoteo.crysis.item.*;
import com.elfoteo.crysis.gui.NanosuitOverlay;

import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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

    public static float absorbDamage(Player player, float incomingDamage, DamageSource source) {
        if (!isArmorMode(player) || !isWearingFullNanosuit(player)) {
            return incomingDamage;
        }

        float absorptionRate = 0.8f; // Default absorption rate
        float energyPerDamage = 1f;  // Energy cost per absorbed damage

        if (source.is(DamageTypes.FALL)) {
            energyPerDamage = 1.5f;
            if (SkillData.isUnlocked(Skill.GROUND_SKIN, player)) {
                absorptionRate = 1.0f;
            } else {
                absorptionRate = 0.0f;
            }
        } else if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.LAVA)) {
            absorptionRate = 1.0f;
            energyPerDamage = 0.2f;
        }

        // Fortified Core effect: reduce energy cost if below 30% health
        if (SkillData.isUnlocked(Skill.FORTIFIED_CORE, player)) {
            float currentHealth = player.getHealth();
            float maxHealth = player.getMaxHealth();
            if (currentHealth <= maxHealth * 0.3f) {
                energyPerDamage *= 0.8f; // Reduce energy cost by 20%
            }
        }

        float absorbed = tryAbsorbDamage(player, incomingDamage, absorptionRate, energyPerDamage);

        // Send updated energy status
        ArmorInfoPacket packet = new ArmorInfoPacket(
                player.getData(ModAttachments.ENERGY),
                player.getData(ModAttachments.MAX_ENERGY),
                player.getData(ModAttachments.MAX_ENERGY_REGEN),
                player.getData(ModAttachments.SUIT_MODE)
        );

        if (player.level().isClientSide) {
            PacketDistributor.sendToServer(packet);
        } else {
            PacketDistributor.sendToPlayer((ServerPlayer) player, packet);
        }

        return incomingDamage - absorbed;
    }

    /**
     * Attempts to absorb damage by draining energy.
     * @param player Player entity
     * @param damage Incoming damage
     * @param absorptionRate Fraction of damage to absorb (e.g., 0.8f = 80%)
     * @param energyPerDamage Energy cost per point of absorbed damage
     * @return The amount of damage successfully absorbed
     */
    private static float tryAbsorbDamage(Player player, float damage, float absorptionRate, float energyPerDamage) {
        float availableEnergy = getAvailableEnergy(player);
        float absorbableDamage = Math.min(damage * absorptionRate, availableEnergy / energyPerDamage);
        float energyCost = absorbableDamage * energyPerDamage;

        if (absorbableDamage > 0) {
            player.setData(ModAttachments.ENERGY, availableEnergy - energyCost);
        }

        return absorbableDamage;
    }

    private static float getAvailableEnergy(Player player) {
        return player.getData(ModAttachments.ENERGY);
    }
}
