package com.elfoteo.crysis.event;

import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillData;
import com.elfoteo.crysis.util.SuitModes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "crysis", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class PowerJumpUpgradeClient {

    /** Tracks "charge amount" from 0→1 while crouching (client‐side). */
    private static final Map<Player, Float> jumpChargeMap = new WeakHashMap<>();

    public static float getCurrentJumpCharge(Player player) {
        return jumpChargeMap.getOrDefault(player, 0f);
    }

    /**
     * Every tick (client‐side), if player is crouching & onGround → charge increases.
     * Otherwise (and not just‐jumped) → charge decays.
     */
    @SubscribeEvent
    public static void onPlayerTick(ClientTickEvent.Post event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!SkillData.isUnlocked(Skill.POWER_JUMP, player)) return;
        if (Nanosuit.currentClientMode == SuitModes.NOT_EQUIPPED.get()) return;

        float charge = getCurrentJumpCharge(player);

        // Check if we just performed a power‐jump within the last tick
        int lastTick = PowerJumpUpgrade.lastJumpTick.getOrDefault(player.getUUID(), -100);
        boolean recentlyJumped = (player.tickCount - lastTick) <= 1;

        if (!recentlyJumped && player.isCrouching() && player.onGround()) {
            // Charge up
            float delta = (1f - charge) * (SkillData.isUnlocked(Skill.QUICK_CHARGE, player) ? 0.1f : 0.05f)
                    + (SkillData.isUnlocked(Skill.QUICK_CHARGE, player) ? 0.02f : 0.01f);
            charge = Math.min(1f, charge + delta);
        } else if (!recentlyJumped) {
            // Decay
            charge = Math.max(0f, charge - 0.1f);
        }

        jumpChargeMap.put(player, charge);
    }

    /**
     * Client‐side hook: apply actual motion when the local player executes a power jump.
     * The "charge" used here was built up in onPlayerTick.
     *
     * IMPORTANT: In multiplayer, we DON'T drain energy here - only the server should do that.
     * We just apply the movement and let the server validate/sync the energy.
     */
    @SubscribeEvent
    public static void onClientJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player != Minecraft.getInstance().player) return;
        if (!player.level().isClientSide) return;
        if (!SkillData.isUnlocked(Skill.POWER_JUMP, player)) return;
        if (!player.isCrouching()) return;
        if (Nanosuit.currentClientMode == SuitModes.NOT_EQUIPPED.get()) return;

        // REMOVED: Energy draining on client - this should only happen on server
        // The server will validate energy and sync it back to the client

        float charge = getCurrentJumpCharge(player);

        // Don't apply movement if charge is too low
        if (charge < 0.1f) return;

        // Compute jump vectors
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

        // Reset charge locally
        jumpChargeMap.put(player, 0f);
    }
}