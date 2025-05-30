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
    // Track previous energy to detect “actual” energy use (with epsilon)
    private static final float ENERGY_EPSILON = 0.0001f;

    // Maps for each player UUID:
    private static final Map<UUID, Float> previousEnergy = new HashMap<>();
    private static final Map<UUID, Integer> ticksSinceEnergyUse = new HashMap<>();
    private static final Map<UUID, Integer> lastDamageTick = new HashMap<>();
    private static final Map<UUID, Vec3> previousPositions = new HashMap<>();

    /**
     * Called every tick **after** the player has been updated.
     * Handles both health (nano) regeneration and energy regeneration.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return; // Only run on server

        UUID uuid = player.getUUID();
        int currentTick = player.tickCount;

        // Fetch suit mode as an int to avoid float‐comparison issues
        int currentMode = (int) player.getData(ModAttachments.SUIT_MODE);

        // =========================
        // -- Health (Nano) Regen --
        // =========================
        // Only if NANO_REGEN unlocked, in Armor mode, >50% energy, not at full health,
        // and at least 60 ticks (3s) since last real damage event, and once per second
        float energy = player.getData(ModAttachments.ENERGY);
        int maxEnergy = player.getData(ModAttachments.MAX_ENERGY);
        float maxRegenRate = player.getData(ModAttachments.MAX_ENERGY_REGEN) * 2; // TEMP: double rate

        int ticksSinceLastHit = currentTick - lastDamageTick.getOrDefault(uuid, -1000);

        if (SkillData.isUnlocked(Skill.NANO_REGEN, player)
                && currentMode == SuitModes.ARMOR.get()
                && energy / maxEnergy > 0.5f
                && player.getHealth() < player.getMaxHealth()
                && ticksSinceLastHit >= 60      // must be ≥3s since last hit
                && currentTick % 20 == 0) {    // once per second
            player.heal(1.0f);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(
                        ParticleTypes.HEART,
                        player.getX(), player.getY() + 1.2, player.getZ(),
                        1, 0.2, 0.3, 0.2, 0.01
                );
            }
        }

        // =========================
        // -- Energy Regeneration --
        // =========================
        // If not in Armor mode, clear per‐player trackers and exit
        if (currentMode != SuitModes.ARMOR.get()) {
            ticksSinceEnergyUse.remove(uuid);
            previousEnergy.remove(uuid);
            lastDamageTick.remove(uuid);
            previousPositions.remove(uuid);
            PowerJumpUpgrade.lastJumpTick.remove(uuid);
            return;
        }

        // --- Compute “idle since energy‐use” with epsilon comparison ---
        float lastEnergy = previousEnergy.getOrDefault(uuid, energy);
        if (lastEnergy - energy > ENERGY_EPSILON) {
            // Energy truly decreased by more than EPS → player used energy
            ticksSinceEnergyUse.put(uuid, 0);
        } else {
            // Energy stayed the same, or slightly changed within EPS → still “idle”
            ticksSinceEnergyUse.merge(uuid, 1, Integer::sum);
        }
        previousEnergy.put(uuid, energy);

        int ticksIdle = ticksSinceEnergyUse.getOrDefault(uuid, 0);

        // --- “Post‐jump” delay logic ---
        int ticksSinceLastJump = currentTick - PowerJumpUpgrade.lastJumpTick.getOrDefault(uuid, -1000);
        int jumpDelay = SkillData.isUnlocked(Skill.FAST_RECOVERY, player) ? 40 : 60;

        // If any of these are true, do NOT regenerate this tick:
        // • player used energy within last 20 ticks (1s)
        // • player was hit within last 20 ticks (1s)
        // • player jumped within last jumpDelay ticks
        if (ticksIdle < 20
                || ticksSinceLastHit < 20
                || ticksSinceLastJump < jumpDelay) {
            return;
        }

        // --- Movement‐based slowdown ---
        Vec3 velocity = getVelocity(player);
        double speedSq = velocity.x * velocity.x + velocity.z * velocity.z;

        float regenRate = maxRegenRate;
        if (speedSq > 0.001 && speedSq < 0.25 && !player.isSprinting()) {
            // Walking slows regen to 50%
            regenRate /= 2f;
        } else if (player.isSprinting()) {
            // Sprinting → no regen
            regenRate = 0f;
        }

        float regenPerTick = regenRate / 20.0f;
        if (energy < maxEnergy && regenPerTick > 0) {
            float newEnergy = Math.min(maxEnergy, energy + regenPerTick);
            player.setData(ModAttachments.ENERGY, newEnergy);

            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(
                        serverPlayer,
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

    /**
     * Tracks when a player actually takes damage. This replaces the old per‐tick
     * hurtTime logic, ensuring we only note the tick of the original damage event.
     */
    @SubscribeEvent
    public static void onPlayerHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        UUID uuid = player.getUUID();
        lastDamageTick.put(uuid, player.tickCount);
    }

    /**
     * Clears all internal tracking whenever a player respawns.
     * Without this, “ticksSinceLastHit” could be negative and block regen for minutes.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        UUID uuid = player.getUUID();
        previousEnergy.remove(uuid);
        ticksSinceEnergyUse.remove(uuid);
        lastDamageTick.remove(uuid);
        previousPositions.remove(uuid);
        PowerJumpUpgrade.lastJumpTick.remove(uuid);
    }

    /**
     * Computes per‐tick horizontal velocity by comparing current vs. previous position.
     * Must update previousPositions each tick.
     */
    private static Vec3 getVelocity(Player player) {
        UUID uuid = player.getUUID();
        Vec3 current = player.position();
        Vec3 previous = previousPositions.getOrDefault(uuid, current);
        previousPositions.put(uuid, current);
        return current.subtract(previous);
    }
}
