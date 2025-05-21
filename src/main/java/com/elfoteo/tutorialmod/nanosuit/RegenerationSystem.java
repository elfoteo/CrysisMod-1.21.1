package com.elfoteo.tutorialmod.nanosuit;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.network.custom.ArmorInfoPacket;
import com.elfoteo.tutorialmod.util.SuitModes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class RegenerationSystem {
    // Using these maps is necessary to track per-player state for regeneration
    // logic
    private static final Map<UUID, Float> previousEnergy = new HashMap<>();
    private static final Map<UUID, Integer> ticksSinceEnergyUse = new HashMap<>();
    // Keep this for velocity check - necessary for speed-based regeneration
    private static final Map<UUID, Vec3> previousPositions = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        Player player = event.getEntity();
        UUID uuid = player.getUUID();
        float currentMode = player.getData(ModAttachments.SUIT_MODE);

        // Regeneration only happens in ARMOR mode
        if (currentMode != SuitModes.ARMOR.get()) {
            // Reset energy usage tracking when switching modes
            ticksSinceEnergyUse.remove(uuid);
            previousEnergy.remove(uuid);
            return;
        }

        float currentEnergy = player.getData(ModAttachments.ENERGY);
        int maxEnergy = player.getData(ModAttachments.MAX_ENERGY);
        float maxRegenRate = player.getData(ModAttachments.MAX_ENERGY_REGEN)*2; // Temporary, please remove the *2 in the future

        // Check if energy was used since the last tick
        float lastEnergy = previousEnergy.getOrDefault(uuid, currentEnergy);
        if (currentEnergy < lastEnergy) {
            ticksSinceEnergyUse.put(uuid, 0); // Reset the counter
        } else {
            ticksSinceEnergyUse.merge(uuid, 1, Integer::sum); // Increment the counter
        }
        previousEnergy.put(uuid, currentEnergy); // Update previous energy for the next tick

        // Only start regenerating after 1 second (20 ticks) of no energy use
        int ticksIdle = ticksSinceEnergyUse.getOrDefault(uuid, 0);
        if (ticksIdle < 20) {
            return;
        }

        // Get current velocity for speed-based regeneration
        Vec3 velocity = getVelocity(player);
        double speedSq = velocity.x * velocity.x + velocity.z * velocity.z;

        float currentRegenRate = maxRegenRate;

        // Adjust regeneration rate based on movement speed
        if (speedSq > 0.001 && speedSq < 0.25 && !player.isSprinting()) {
            // Player is walking, reduce regen rate
            currentRegenRate /= 2f;
        } else if (player.isSprinting()) {
            // No regeneration while sprinting
            currentRegenRate = 0f;
        }

        // Calculate regeneration amount for this tick
        float regenAmountPerTick = currentRegenRate / 20.0f;

        // Apply regeneration if energy is below max and there's a positive regen rate
        if (currentEnergy < maxEnergy && regenAmountPerTick > 0) {
            float newEnergy = Math.min((float) maxEnergy, currentEnergy + regenAmountPerTick);
            player.setData(ModAttachments.ENERGY, newEnergy);

            // Send update to client
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer,
                        new ArmorInfoPacket(
                                newEnergy,
                                maxEnergy,
                                player.getData(ModAttachments.MAX_ENERGY_REGEN),
                                player.getData(ModAttachments.SUIT_MODE)));
            }
        }
    }

    // Helper method to calculate player velocity
    private static Vec3 getVelocity(Player player) {
        UUID playerUUID = player.getUUID();
        Vec3 currentPos = player.position();
        Vec3 previousPos = previousPositions.getOrDefault(playerUUID, currentPos);
        previousPositions.put(playerUUID, currentPos); // Update for the next tick
        return currentPos.subtract(previousPos);
    }
}
