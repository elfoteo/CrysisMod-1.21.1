package com.elfoteo.crysis.nanosuit;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillData;
import com.elfoteo.crysis.util.SuitModes;
import com.elfoteo.crysis.util.SuitUtils;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

@EventBusSubscriber(modid = CrysisMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class NanosuitUpgrades {
    private static final ResourceLocation boostResourceLocation = ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "sprint_boost_nanosuit_upgrade");

    @SubscribeEvent()
    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();

        if (!(attacker instanceof Player player)) return;

        // Must be cloaked nanosuit mode
        if (player.getData(ModAttachments.SUIT_MODE) != SuitModes.CLOAK.get()) return;

        // Only apply for melee damage types
        if (!source.is(DamageTypes.PLAYER_ATTACK)
                && !source.is(DamageTypes.MOB_ATTACK)
                && !source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) return;

        // Player must have Ghost Titan skill unlocked
        if (!player.getData(ModAttachments.ALL_SKILLS)
                .get(Skill.GHOST_TITAN).isUnlocked()) return;

        // Add 30% bonus damage on melee attacks while cloaked
        float bonus = event.getAmount() * 1.3f;
        event.setAmount(bonus);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player.getData(ModAttachments.SUIT_MODE) == SuitModes.NOT_EQUIPPED.get()) return;

        // Ensure server and client both can apply attribute changes safely
        if (!player.level().isClientSide) {
            if (SkillData.isUnlocked(Skill.SPRINT_BOOST, player)) {
                // Apply sprint boost effect on server side
                AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    double boost = 0.2; // 20% boost
                    if (speedAttr.getModifier(boostResourceLocation) == null) {
                        AttributeModifier sprintBoostModifier = new AttributeModifier(boostResourceLocation, boost, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                        speedAttr.addTransientModifier(sprintBoostModifier);
                    }
                }
            } else {
                // Remove boost modifier if present
                AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    speedAttr.removeModifier(boostResourceLocation);
                }
            }
        }
    }

    @SubscribeEvent
    public static void armorUpUpgrade(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.getData(ModAttachments.SUIT_MODE) != SuitModes.ARMOR.get()) return;
        if (SkillData.isUnlocked(Skill.ARMOR_UP, player)) {
            float originalDamage = event.getAmount();
            float reducedDamage = originalDamage * 0.9f; // 10% flat resistance
            event.setAmount(reducedDamage);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onShockAbsorption(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.getData(ModAttachments.SUIT_MODE) == SuitModes.NOT_EQUIPPED.get()) return;
        if (!SkillData.isUnlocked(Skill.SHOCK_ABSORPTION, player)) return;

        // Estimate the fall distance based on incoming damage
        float fallDistance = event.getDistance();

        if (fallDistance < 10f && fallDistance > 3f) {
            event.setCanceled(true); // Cancel the fall damage entirely
            player.level().playSound(null, player.blockPosition(), SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, 0.5f, 1.1f);
        }
    }

    private static final class DelayedKnockback {
        int delayTicks;
        Vec3 direction; // Normalized direction from player to entity

        DelayedKnockback(int delayTicks, Vec3 direction) {
            this.delayTicks = delayTicks;
            this.direction = direction;
        }
    }

    private static final Map<LivingEntity, PendingShockwaveEffect> pendingShockwaves = new HashMap<>();

    private record PendingShockwaveEffect(int delayTicks, Vec3 direction, float damage) {}

    @SubscribeEvent
    public static void onShockwaveSlam(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.getData(ModAttachments.SUIT_MODE) == SuitModes.NOT_EQUIPPED.get()) return;
        if (!SkillData.isUnlocked(Skill.SHOCKWAVE_SLAM, player)) return;
        if (player.level().isClientSide) return; // Only on server

        float fallDistance = event.getDistance();
        if (fallDistance < 10f) return;

        if (!(player.level() instanceof ServerLevel level)) return;

        Vec3 playerPos = player.position();

        float radius = Math.min(4f + (fallDistance - 10f) * 0.4f, 12f);
        float maxDamage = Math.min(4f + (fallDistance - 10f) * 1.2f, 18f);

        // Sound & Particles
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1f, 0.9f);
        level.sendParticles(ParticleTypes.EXPLOSION, playerPos.x, playerPos.y, playerPos.z, 20, 0.5, 0.1, 0.5, 0.05);
        level.sendParticles(ParticleTypes.CLOUD, playerPos.x, playerPos.y, playerPos.z, 40, 1, 0.1, 1, 0.1);
        BlockState blockState = Blocks.STONE.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState), playerPos.x, playerPos.y + 0.5, playerPos.z, 30, 1.0, 0.5, 1.0, 0.1);

        AABB aoeBox = new AABB(player.blockPosition()).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, aoeBox)) {
            if (entity == player || entity.isAlliedTo(player)) continue;

            double dx = entity.getX() - player.getX();
            double dz = entity.getZ() - player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > radius) continue;

            float scaledDamage = maxDamage * (1.0f - (float)(distance / radius));

            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                    entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                    10, 0.3, 0.3, 0.3, 0.05);

            int delayTicks = Math.max(0, (int) (distance - 1));
            Vec3 direction = new Vec3(dx, 0, dz).normalize();

            pendingShockwaves.put(entity, new PendingShockwaveEffect(delayTicks, direction, scaledDamage));
        }
    }

    @SubscribeEvent
    public static void onServerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (pendingShockwaves.isEmpty()) return;

        List<LivingEntity> toRemove = new ArrayList<>();
        pendingShockwaves.forEach((entity, effect) -> {
            if (!entity.isAlive()) {
                toRemove.add(entity);
                return;
            }

            if (effect.delayTicks() <= 0) {
                // Apply knockback
                double knockUp = 0.6;
                double knockBackStrength = 0.6;
                Vec3 pushVec = effect.direction().scale(knockBackStrength).add(0, knockUp, 0);
                entity.push(pushVec.x, pushVec.y, pushVec.z);

                // Apply delayed damage
                Entity attacker = event.getEntity();
                if (attacker instanceof Player player) {
                    entity.hurt(player.damageSources().mobAttack(player), effect.damage());
                }

                toRemove.add(entity);
            } else {
                pendingShockwaves.put(entity, new PendingShockwaveEffect(
                        effect.delayTicks() - 1, effect.direction(), effect.damage()));
            }
        });

        toRemove.forEach(pendingShockwaves::remove);
    }

    private static final Map<UUID, Integer> powerSurgeTimers = new HashMap<>();
    private static final Set<UUID> powerSurgeUsed = new HashSet<>();

    @SubscribeEvent
    public static void onMeleeKill(LivingDeathEvent event) {
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player player)) return;

        if (!SkillData.isUnlocked(Skill.POWER_SURGE, player)) return;

        DamageSource damageSource = event.getSource();
        if (!damageSource.is(DamageTypes.PLAYER_ATTACK)
                && !damageSource.is(DamageTypes.MOB_ATTACK)
                && !damageSource.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) return;

        // Start 5 second (100 tick) power surge timer
        UUID uuid = player.getUUID();
        powerSurgeTimers.put(uuid, 100);
        powerSurgeUsed.remove(uuid);
    }

    @SubscribeEvent
    public static void onTickPowerSurge(PlayerTickEvent.Pre event) {
        UUID uuid = event.getEntity().getUUID();
        if (powerSurgeTimers.containsKey(uuid)) {
            int remaining = powerSurgeTimers.get(uuid) - 1;
            if (remaining <= 0) {
                powerSurgeTimers.remove(uuid);
                powerSurgeUsed.remove(uuid);
            } else {
                powerSurgeTimers.put(uuid, remaining);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKineticPunchDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) return;

        DamageSource source = event.getSource();

        // Check it's a true melee hit: direct entity must be the player and the source type is PLAYER_ATTACK
        if (!source.is(DamageTypes.PLAYER_ATTACK) || source.getDirectEntity() != player) return;

        if (SkillData.isUnlocked(Skill.KINETIC_PUNCH, player)) {
            LivingEntity target = event.getEntity();
            float health = target.getHealth();
            float damage = event.getAmount();

            if (target != player && !target.isAlliedTo(player)) {
                // Instantly kill weak mobs (less than 6 HP)
                if (health < 6.0f) {
                    event.setAmount(health + 1.0f); // Overkill to ensure death
                }

                // Apply horizontal knockback
                Vec3 dir = target.position().subtract(player.position()).normalize().scale(1.0);
                target.push(dir.x, 0.3, dir.z);
            }
        }

        // Power Surge enhancement
        UUID uuid = player.getUUID();
        if (SkillData.isUnlocked(Skill.POWER_SURGE, player) &&
                powerSurgeTimers.containsKey(uuid) &&
                !powerSurgeUsed.contains(uuid)) {

            // Apply +50% bonus
            event.setAmount(event.getAmount() * 1.5f);
            powerSurgeUsed.add(uuid);

            // Visual feedback
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
                level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 1.5f);
            }
        }
    }
}