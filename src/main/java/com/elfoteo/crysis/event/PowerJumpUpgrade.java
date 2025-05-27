package com.elfoteo.crysis.event;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillData;
import com.elfoteo.crysis.util.SuitModes;
import com.elfoteo.crysis.util.SuitUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

import java.util.*;

@EventBusSubscriber(modid = CrysisMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class PowerJumpUpgrade {
    private static final float BASE_JUMP_COST = 20f;

    /** Tracks “charge amount” from 0→1 while crouching. */
    private static final Map<Player, Float> jumpChargeMap = new WeakHashMap<>();

    /** Records the tick of the most recent server‐side power jump for each player. */
    public static final Map<UUID, Integer> lastJumpTick = new HashMap<>();

    public static float getCurrentJumpCharge(Player player) {
        return jumpChargeMap.getOrDefault(player, 0f);
    }

    /**
     * Every tick (client‐side), if player is crouching & onGround → charge increases.
     * Otherwise (and not just‐jumped) → charge decays.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        // Only proceed if this player has unlocked POWER_JUMP
        if (!SkillData.isUnlocked(Skill.POWER_JUMP, player)) return;

        // Only run on the controlling client
        if (player != Minecraft.getInstance().player) return;
        if (Nanosuit.currentClientMode == SuitModes.NOT_EQUIPPED.get()) return;

        float charge = getCurrentJumpCharge(player);

        // Check if we just performed a power‐jump within the last tick
        int lastTick = lastJumpTick.getOrDefault(player.getUUID(), -100);
        boolean recentlyJumped = (player.tickCount - lastTick) <= 1;

        // If we did not just jump this tick AND player is crouching & onGround → charge up
        if (!recentlyJumped && player.isCrouching() && player.onGround()) {
            // Base charge increment per tick (without QUICK_CHARGE):
            //   delta = (1 – charge) * 0.2f + 0.05f
            float baseDelta;

            // If QUICK_CHARGE is unlocked, multiply charge‐gain by 1.5x (i.e. ~33% faster)
            if (SkillData.isUnlocked(Skill.QUICK_CHARGE, player)) {
                baseDelta = (1f - charge) * 0.2f + 0.02f;
            }
            else {
                baseDelta = (1f - charge) * 0.1f + 0.01f;
            }

            charge = Math.min(1f, charge + baseDelta);
        }
        // If not crouching & not recently jumped, let charge decay
        else if (!recentlyJumped) {
            // you could also factor QUICK_CHARGE here if desired,
            // but for now we keep decay constant:
            charge = Math.max(0f, charge - 0.1f);
        }

        jumpChargeMap.put(player, charge);
    }

    /**
     * Server‐side hook: When a “living jump” event fires, check if crouching + POWER_JUMP.
     * If so: attempt to drain energy (possibly reduced by EFFICIENT_BURST), zero out charge, and record tick.
     */
    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        // Only server‐side
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Must have POWER_JUMP unlocked & be crouching & nanosuit equipped
        if (!SkillData.isUnlocked(Skill.POWER_JUMP, player)) return;
        if (!player.isCrouching()) return;
        if (Nanosuit.currentClientMode == SuitModes.NOT_EQUIPPED.get()) return;

        // Determine actual energy cost
        float cost = BASE_JUMP_COST;
        if (SkillData.isUnlocked(Skill.EFFICIENT_BURST, player)) {
            cost *= 0.7f; // –30% cost
        }

        // If insufficient energy, skip the power jump entirely
        if (!SuitUtils.tryDrainEnergy(player, cost)) {
            return;
        }

        // Zero out charge buffer and record tick so client can delay next charge
        jumpChargeMap.put(player, 0f);
        lastJumpTick.put(player.getUUID(), player.tickCount);
    }

    /**
     * Client‐side hook: apply actual motion when the local player executes a power jump.
     * The “charge” used here was built up in onPlayerTick. We apply BOOST if POWER_AMPLIFIER is unlocked.
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player != Minecraft.getInstance().player) return;
        if (!player.level().isClientSide) return;

        // Must have POWER_JUMP unlocked & be crouching & nanosuit equipped
        if (!SkillData.isUnlocked(Skill.POWER_JUMP, player)) return;
        if (!player.isCrouching()) return;
        if (Nanosuit.currentClientMode == SuitModes.NOT_EQUIPPED.get()) return;

        // Drain energy clientside as well (same cost logic as server)
        float cost = BASE_JUMP_COST;
        if (SkillData.isUnlocked(Skill.EFFICIENT_BURST, player)) {
            cost *= 0.7f;
        }
        if (!SuitUtils.tryDrainEnergy(player, cost)) {
            return;
        }

        // Fetch current charge (0→1)
        float charge = getCurrentJumpCharge(player);

        // Compute base vertical/horiz velocities:
        float pitch = player.getXRot() * ((float) Math.PI / 180f);
        float yaw   = player.getYRot() * ((float) Math.PI / 180f);

        // Base vertical component:  (–sin(pitch)*0.8 + 1) * charge
        float baseVY = Mth.clamp((-Mth.sin(pitch) * 0.8f + 1f) * charge, 0f, Float.MAX_VALUE);
        // Base horizontal component: cos(pitch) * charge * 1.5f
        float baseHoriz = Mth.cos(pitch) * charge * 1.5f;


        if (SkillData.isUnlocked(Skill.POWER_AMPLIFIER, player)) {
            baseVY = Mth.clamp((-Mth.sin(pitch) * 0.95f + 1f) * charge, 0f, Float.MAX_VALUE);
            baseHoriz = Mth.cos(pitch) * charge * 1.7f;
        }

        Vec3 vel = player.getDeltaMovement();
        player.setDeltaMovement(
                vel.x + -Mth.sin(yaw) * baseHoriz,
                baseVY,
                vel.z +  Mth.cos(yaw) * baseHoriz
        );
        player.hasImpulse = true;

        // Reset charge locally (server already zeroed it), so next tick the client knows it just jumped
        jumpChargeMap.put(player, 0f);
    }
}