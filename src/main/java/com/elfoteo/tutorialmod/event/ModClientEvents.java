package com.elfoteo.tutorialmod.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "tutorialmod", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ModClientEvents {
    public static final boolean FAST_COMPUTE = true;
    private static final int ACCUM_TICKS = 20;  // Ticks to reach target heat
    private static final int FADE_TICKS = 20;  // Ticks to fade out
    private static final float THRESHOLD = 0.03f;  // Threshold for heat to be removed/added to the ideal heat map

    // Current heat levels
    private static final ConcurrentHashMap<BlockPos, Float> HEAT_CACHE = new ConcurrentHashMap<>();
    // Target heat levels
    private static final ConcurrentHashMap<BlockPos, Float> IDEAL_HEAT_MAP = new ConcurrentHashMap<>();

    /**
     * Called during rendering to get current heat and set target heat.
     */
    public static float getHeat(BlockPos pos, float targetHeat) {
        if (FAST_COMPUTE) return targetHeat;
        float currentHeat = HEAT_CACHE.getOrDefault(pos, 0f);
        if (Math.abs(currentHeat - targetHeat) > THRESHOLD){
            IDEAL_HEAT_MAP.put(pos, targetHeat);  // Mark block heat to update over time since it is not already the target
        }
        return currentHeat;
    }

    @SubscribeEvent
    public static void updateHeatTick(ClientTickEvent.Post event) {
        if (FAST_COMPUTE) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LevelRenderer renderer = mc.levelRenderer;
        if (level == null) return;

        // Collect unique section coordinates to mark as dirty
        var dirtySections = new java.util.HashSet<Long>();

        IDEAL_HEAT_MAP.forEach((pos, targetHeat) -> {
            float currentHeat = HEAT_CACHE.getOrDefault(pos, 0f);
            float nextHeat;

            if (currentHeat < targetHeat) {
                float gain = (targetHeat - currentHeat) / ACCUM_TICKS;
                nextHeat = Math.min(targetHeat, currentHeat + gain);
            } else if (currentHeat > targetHeat) {
                float loss = (currentHeat - targetHeat) / FADE_TICKS;
                nextHeat = Math.max(targetHeat, currentHeat - loss);
            } else {
                nextHeat = currentHeat;
            }

            if (nextHeat > 0f) {
                HEAT_CACHE.put(pos, nextHeat);
            } else {
                HEAT_CACHE.remove(pos);
            }

            // Track dirty section based on block position
            int sectionX = pos.getX() >> 4;
            int sectionY = pos.getY() >> 4;
            int sectionZ = pos.getZ() >> 4;
            long sectionKey = (((long)sectionX & 0x3FFFFL) << 40) |
                    (((long)sectionY & 0xFFL) << 32) |
                    ((long)sectionZ & 0xFFFFFFFFL);
            dirtySections.add(sectionKey);

            // Remove from IDEAL_HEAT_MAP if target reached
            if (Math.abs(nextHeat - targetHeat) < THRESHOLD) {
                IDEAL_HEAT_MAP.remove(pos);
            }
        });

        // Mark sections dirty only once per section
        for (long key : dirtySections) {
            int sectionX = (int)((key >> 40) & 0x3FFFFL);
            int sectionY = (int)((key >> 32) & 0xFFL);
            int sectionZ = (int)(key & 0xFFFFFFFFL);
            renderer.setSectionDirty(sectionX, sectionY, sectionZ);
        }
    }
}