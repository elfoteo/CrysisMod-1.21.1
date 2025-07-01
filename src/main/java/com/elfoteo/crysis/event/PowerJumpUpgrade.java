package com.elfoteo.crysis.event;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillData;
import com.elfoteo.crysis.util.SuitModes;
import com.elfoteo.crysis.util.SuitUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "crysis", bus = EventBusSubscriber.Bus.GAME, value = Dist.DEDICATED_SERVER)
public class PowerJumpUpgrade {
    private static final float BASE_JUMP_COST = 20f;

    /** Records the tick of the most recent server‐side power jump for each player. */
    public static final Map<UUID, Integer> lastJumpTick = new HashMap<>();

    /** Server-side charge tracking - mirrors client but authoritative */
    private static final Map<UUID, Float> serverJumpChargeMap = new HashMap<>();

    public static float getServerJumpCharge(ServerPlayer player) {
        return serverJumpChargeMap.getOrDefault(player.getUUID(), 0f);
    }

    /**
     * Server-side charge tracking to mirror client behavior
     */
    @SubscribeEvent
    public static void onServerPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!SkillData.isUnlocked(Skill.POWER_JUMP, player)) return;
        if (player.getData(ModAttachments.SUIT_MODE) == SuitModes.NOT_EQUIPPED.get()) return;

        UUID playerId = player.getUUID();
        float charge = serverJumpChargeMap.getOrDefault(playerId, 0f);

        // Check if we just performed a power‐jump within the last tick
        int lastTick = lastJumpTick.getOrDefault(playerId, -100);
        boolean recentlyJumped = (player.tickCount - lastTick) <= 1;

        if (!recentlyJumped && player.isCrouching() && player.onGround()) {
            // Charge up - match client calculation
            float delta = (1f - charge) * (SkillData.isUnlocked(Skill.QUICK_CHARGE, player) ? 0.1f : 0.05f)
                    + (SkillData.isUnlocked(Skill.QUICK_CHARGE, player) ? 0.02f : 0.01f);
            charge = Math.min(1f, charge + delta);
        } else if (!recentlyJumped) {
            // Decay - match client calculation
            charge = Math.max(0f, charge - 0.1f);
        }

        serverJumpChargeMap.put(playerId, charge);
    }

    /**
     * Server‐side hook: When a "living jump" event fires, check if crouching + POWER_JUMP.
     * If so: attempt to drain energy (possibly reduced by EFFICIENT_BURST), apply movement, and record tick.
     */
    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        // Only run on the server
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!SkillData.isUnlocked(Skill.POWER_JUMP, player)) {
            return;
        }
        if (!player.isCrouching()) {
            System.out.println("Player is not crouching");
            return;
        }
        System.out.println("Player is crouching");

        if (player.getData(ModAttachments.SUIT_MODE) == SuitModes.NOT_EQUIPPED.get()) {
            System.out.println("Nanosuit is NOT_EQUIPPED");
            return;
        }

        UUID playerId = player.getUUID();
        float charge = getServerJumpCharge(player);

        // Don't allow jump if charge is too low
        if (charge < 0.1f) return;

        // Determine actual energy cost
        float cost = BASE_JUMP_COST;
        if (SkillData.isUnlocked(Skill.EFFICIENT_BURST, player)) {
            cost *= 0.7f; // –30% cost
        }

        // If insufficient energy, skip the power jump entirely
        if (!SuitUtils.tryDrainEnergy(player, cost)) {
            return;
        }

        // Apply server-side movement (this will sync to client automatically)
        float pitch = player.getXRot() * ((float) Math.PI / 180f);
        float yaw   = player.getYRot() * ((float) Math.PI / 180f);

        float baseVY    = Mth.clamp((-Mth.sin(pitch) * 0.8f + 1f) * charge, 0f, Float.MAX_VALUE);
        float baseHoriz = Mth.cos(pitch) * charge * 1.5f;
        if (SkillData.isUnlocked(Skill.POWER_AMPLIFIER, player)) {
            baseVY    = Mth.clamp((-Mth.sin(pitch) * 0.95f + 1f) * charge, 0f, Float.MAX_VALUE);
            baseHoriz = Mth.cos(pitch) * charge * 1.7f;
        }

        Vec3 vel = player.getDeltaMovement();
        player.setDeltaMovement(
                vel.x + -Mth.sin(yaw) * baseHoriz,
                baseVY,
                vel.z +  Mth.cos(yaw) * baseHoriz
        );
        player.hasImpulse = true;

        // Reset server charge
        serverJumpChargeMap.put(playerId, 0f);

        // Record tick of this jump so clients know when to reset their charge
        lastJumpTick.put(playerId, player.tickCount);
    }
}