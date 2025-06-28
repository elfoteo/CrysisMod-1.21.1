package com.elfoteo.crysis.event;

import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillData;
import com.elfoteo.crysis.util.SuitModes;
import com.elfoteo.crysis.util.SuitUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "crysis", bus = EventBusSubscriber.Bus.GAME, value = Dist.DEDICATED_SERVER)
public class PowerJumpUpgrade {
    private static final float BASE_JUMP_COST = 20f;

    /** Records the tick of the most recent server‐side power jump for each player. */
    public static final Map<UUID, Integer> lastJumpTick = new HashMap<>();

    /**
     * Server‐side hook: When a “living jump” event fires, check if crouching + POWER_JUMP.
     * If so: attempt to drain energy (possibly reduced by EFFICIENT_BURST), zero out charge, and record tick.
     */
    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        // Only run on the server
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

        // Record tick of this jump so clients know when to reset their charge
        lastJumpTick.put(player.getUUID(), player.tickCount);
    }
}
