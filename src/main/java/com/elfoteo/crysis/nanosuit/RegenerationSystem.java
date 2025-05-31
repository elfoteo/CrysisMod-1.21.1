package com.elfoteo.crysis.nanosuit;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.event.PowerJumpUpgrade;
import com.elfoteo.crysis.network.custom.ArmorInfoPacket;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillData;
import com.elfoteo.crysis.util.SuitModes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CrysisMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class RegenerationSystem {
    private static final float ENERGY_EPSILON = 0.0001f;
    private static final int HEALTH_HIT_DELAY = 60;          // ticks (3s)
    private static final int HEALTH_REGEN_INTERVAL_NANO = 20; // ticks (1s)
    private static final int HEALTH_REGEN_INTERVAL_NO_NANO = 40; // ticks (2s)
    private static final int HUNGER_REGEN_BASE = 100;        // ticks (30s)
    private static final float HUNGER_SATURATION = 0.05f;
    private static final int MAX_FOOD_LEVEL = 20;

    private static final Map<UUID, Float> previousEnergy = new HashMap<>();
    private static final Map<UUID, Integer> ticksSinceEnergyUse = new HashMap<>();
    private static final Map<UUID, Integer> lastDamageTick = new HashMap<>();
    private static final Map<UUID, Vec3> previousPositions = new HashMap<>();
    private static final Map<UUID, Integer> fullEnergyTicks = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        UUID uuid = player.getUUID();
        int tick = player.tickCount;
        int suitMode = player.getData(ModAttachments.SUIT_MODE);

        // Track damage timing
        int sinceLastHit = tick - lastDamageTick.getOrDefault(uuid, -1000);

        // Health regeneration (only in ARMOR mode and if below max health)
        if (suitMode == SuitModes.ARMOR.get() && player.getHealth() < player.getMaxHealth()) {
            float energy    = player.getData(ModAttachments.ENERGY);
            int   maxEnergy = player.getData(ModAttachments.MAX_ENERGY);

            if (SkillData.isUnlocked(Skill.NANO_REGEN, player)) {
                // Nano regen: >50% energy, no damage in last 60 ticks, once per second
                if (energy / maxEnergy > 0.5f
                        && sinceLastHit >= HEALTH_HIT_DELAY
                        && tick % HEALTH_REGEN_INTERVAL_NANO == 0) {

                    player.heal(1.0f);
                    spawnHeartParticle(player);
                }
            } else {
                // No nano upgrade: must have max energy held, no damage in last 60 ticks, once per 2 seconds
                if (energy >= maxEnergy - ENERGY_EPSILON) {
                    fullEnergyTicks.merge(uuid, 1, Integer::sum);
                } else {
                    fullEnergyTicks.put(uuid, 0);
                }

                if (fullEnergyTicks.getOrDefault(uuid, 0) >= 1
                        && sinceLastHit >= HEALTH_HIT_DELAY
                        && tick % HEALTH_REGEN_INTERVAL_NO_NANO == 0) {

                    player.heal(1.0f);
                    spawnHeartParticle(player);
                }
            }
        }

        // Hunger regeneration if at full health
        if (player.getHealth() >= player.getMaxHealth()) {
            int hungerInterval = HUNGER_REGEN_BASE;
            float saturation = HUNGER_SATURATION;
            if (!SkillData.isUnlocked(Skill.NANO_REGEN, player)) {
                hungerInterval *= 2; // double cooldown if no nano regen unlocked
                saturation *= 2; // double cooldown if no nano regen unlocked
            }

            if (player.getFoodData().getFoodLevel() < MAX_FOOD_LEVEL
                    && tick % hungerInterval == 0) {
                player.getFoodData().eat(1, saturation);
            }
        }

        // Energy regeneration and tracking (only in ARMOR mode)
        if (suitMode != SuitModes.ARMOR.get()) {
            clearTrackers(uuid);
            return;
        }

        float energy    = player.getData(ModAttachments.ENERGY);
        int   maxEnergy = player.getData(ModAttachments.MAX_ENERGY);
        float maxRegen  = player.getData(ModAttachments.MAX_ENERGY_REGEN) * 2;

        float lastEnergy = previousEnergy.getOrDefault(uuid, energy);
        if (lastEnergy - energy > ENERGY_EPSILON) {
            ticksSinceEnergyUse.put(uuid, 0);
            fullEnergyTicks.put(uuid, 0);
        } else {
            ticksSinceEnergyUse.merge(uuid, 1, Integer::sum);
        }
        previousEnergy.put(uuid, energy);

        int idleTicks = ticksSinceEnergyUse.getOrDefault(uuid, 0);
        int sinceLastJump = tick - PowerJumpUpgrade.lastJumpTick.getOrDefault(uuid, -1000);
        int jumpDelay = SkillData.isUnlocked(Skill.FAST_RECOVERY, player) ? 40 : 60;

        if (idleTicks < 20 || sinceLastHit < 20 || sinceLastJump < jumpDelay) {
            return;
        }

        Vec3 velocity = getVelocity(player);
        double speedSq = velocity.x * velocity.x + velocity.z * velocity.z;

        float regenRate = maxRegen;
        if (speedSq > 0.001 && speedSq < 0.25 && !player.isSprinting()) {
            regenRate /= 2f;
        } else if (player.isSprinting()) {
            regenRate = 0f;
        }

        float perTick = regenRate / 20f;
        if (energy < maxEnergy && perTick > 0) {
            float newEnergy = Math.min(maxEnergy, energy + perTick);
            player.setData(ModAttachments.ENERGY, newEnergy);

            if (player instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(
                        sp,
                        new ArmorInfoPacket(
                                newEnergy,
                                maxEnergy,
                                player.getData(ModAttachments.MAX_ENERGY_REGEN),
                                player.getData(ModAttachments.SUIT_MODE)
                        )
                );
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        lastDamageTick.put(player.getUUID(), player.tickCount);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        clearTrackers(player.getUUID());
        PowerJumpUpgrade.lastJumpTick.remove(player.getUUID());
    }

    private static Vec3 getVelocity(Player player) {
        UUID uuid = player.getUUID();
        Vec3 current = player.position();
        Vec3 previous = previousPositions.getOrDefault(uuid, current);
        previousPositions.put(uuid, current);
        return current.subtract(previous);
    }

    private static void spawnHeartParticle(Player player) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(
                    ParticleTypes.HEART,
                    player.getX(),
                    player.getY() + 1.2,
                    player.getZ(),
                    1,
                    0.2,
                    0.3,
                    0.2,
                    0.01
            );
        }
    }

    private static void clearTrackers(UUID uuid) {
        previousEnergy.remove(uuid);
        ticksSinceEnergyUse.remove(uuid);
        lastDamageTick.remove(uuid);
        previousPositions.remove(uuid);
        fullEnergyTicks.remove(uuid);
    }
}
