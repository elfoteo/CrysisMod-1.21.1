package com.elfoteo.tutorialmod.nanosuit;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.network.custom.ArmorInfoPacket;
import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.skill.SkillData;
import com.elfoteo.tutorialmod.util.SuitModes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class RegenerationSystem {
    private static final Map<UUID, Float> previousEnergy = new HashMap<>();
    private static final Map<UUID, Integer> ticksSinceEnergyUse = new HashMap<>();
    private static final Map<UUID, Integer> lastDamageTick = new HashMap<>();
    private static final Map<UUID, Vec3> previousPositions = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        UUID uuid = player.getUUID();
        int currentTick = player.tickCount;
        float currentMode = player.getData(ModAttachments.SUIT_MODE);

        // Track when damaged (stored externally, could be event-driven too)
        if (player.hurtTime > 0) {
            lastDamageTick.put(uuid, currentTick);
        }

        float energy = player.getData(ModAttachments.ENERGY);
        int maxEnergy = player.getData(ModAttachments.MAX_ENERGY);
        float maxRegenRate = player.getData(ModAttachments.MAX_ENERGY_REGEN) * 2; // TEMP: double rate

        float lastEnergy = previousEnergy.getOrDefault(uuid, energy);
        if (energy < lastEnergy) {
            ticksSinceEnergyUse.put(uuid, 0);
        } else {
            ticksSinceEnergyUse.merge(uuid, 1, Integer::sum);
        }
        previousEnergy.put(uuid, energy);

        Vec3 velocity = getVelocity(player);
        double speedSq = velocity.x * velocity.x + velocity.z * velocity.z;

        int ticksSinceLastHit = currentTick - lastDamageTick.getOrDefault(uuid, -1000);

        // ===== Nano Regeneration (health) =====
        if (SkillData.isUnlocked(Skill.NANO_REGEN, player)
                && currentMode == SuitModes.ARMOR.get()
                && energy / maxEnergy > 0.5f
                && player.getHealth() < player.getMaxHealth()
                && ticksSinceLastHit >= 60 // 3 seconds
                && currentTick % 20 == 0) {

            player.heal(1.0f);

            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HEART,
                        player.getX(), player.getY() + 1.2, player.getZ(),
                        1, 0.2, 0.3, 0.2, 0.01);
            }
        }

        // ===== Energy Regeneration =====
        if (currentMode != SuitModes.ARMOR.get()) {
            // Reset when not in Armor mode
            ticksSinceEnergyUse.remove(uuid);
            previousEnergy.remove(uuid);
            return;
        }

        int ticksIdle = ticksSinceEnergyUse.getOrDefault(uuid, 0);
        if (ticksIdle < 20 || ticksSinceLastHit < 20) return; // 1s energy delay & hit cooldown

        float regenRate = maxRegenRate;

        if (speedSq > 0.001 && speedSq < 0.25 && !player.isSprinting()) {
            regenRate /= 2f; // Walking
        } else if (player.isSprinting()) {
            regenRate = 0f; // Sprinting = no regen
        }

        float regenPerTick = regenRate / 20.0f;

        if (energy < maxEnergy && regenPerTick > 0) {
            float newEnergy = Math.min(maxEnergy, energy + regenPerTick);
            player.setData(ModAttachments.ENERGY, newEnergy);

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

    private static Vec3 getVelocity(Player player) {
        UUID uuid = player.getUUID();
        Vec3 current = player.position();
        Vec3 previous = previousPositions.getOrDefault(uuid, current);
        previousPositions.put(uuid, current);
        return current.subtract(previous);
    }
}
