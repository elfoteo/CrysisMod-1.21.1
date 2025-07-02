package com.elfoteo.crysis.util;

import com.elfoteo.crysis.CrysisMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.nio.FloatBuffer;
import java.util.*;

/**
 * Manages entity data for rendering, handling prioritization and sending entity positions to shaders.
 * Sends up to 170 entities per frame packed into a 512‑float array, with stable round‑robin ordering.
 */
@EventBusSubscriber(modid = CrysisMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class TrailTextureManager {
    private static final Minecraft mc = Minecraft.getInstance();

    // Timing (per-RenderType)
    private static final Map<Object, Long> lastCallTimes = new HashMap<>();
    private static final Map<Object, Float> cachedDeltaTimes = new HashMap<>();

    // Cull & priority thresholds
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 512;
    private static final int TEXTURE_DEPTH = 384;
    private static final double NEARBY_DISTANCE = 64.0;
    private static final double MEDIUM_DISTANCE = 128.0;

    // Maximums
    private static final int MAX_ENTITIES = 170;
    private static final int DATA_ARRAY_SIZE = 512; // floats

    // Working lists
    private static final List<Entity> combinedEntities = new ArrayList<>();
    private static int roundRobinIndex = 0;

    private TrailTextureManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static float getDeltaTimeFor(Object renderType) {
        long now = System.nanoTime();
        Long last = lastCallTimes.get(renderType);
        if (last == null) {
            lastCallTimes.put(renderType, now);
            cachedDeltaTimes.put(renderType, 0f);
            return 0f;
        }
        float dt = (now - last) / 1_000_000_000f;
        lastCallTimes.put(renderType, now);
        cachedDeltaTimes.put(renderType, dt);
        return dt;
    }

    public static boolean emitsHeat(Entity e) {
        return !(e instanceof Arrow) && !(e instanceof SpectralArrow) &&
                !(e instanceof SnowGolem) && !(e instanceof ExperienceOrb) &&
                !(e instanceof ItemEntity) && !(e instanceof ThrownPotion) &&
                !(e instanceof ThrownTrident) && !(e instanceof ThrownEnderpearl) &&
                !(e instanceof ThrownEgg) && !(e instanceof Snowball) &&
                !(e instanceof ThrownExperienceBottle) && !(e instanceof SmallFireball) &&
                !(e instanceof WitherSkull) && !(e instanceof EvokerFangs) &&
                !(e instanceof AreaEffectCloud) && !(e instanceof LightningBolt) &&
                !(e instanceof FishingHook) && !(e instanceof AbstractMinecart) &&
                !(e instanceof Boat) && !(e instanceof Painting) &&
                !(e instanceof ItemFrame) && !(e instanceof ArmorStand) &&
                !(e instanceof Marker) && !(e instanceof EndCrystal) &&
                !(e instanceof FireworkRocketEntity) &&
                !(e instanceof Display.BlockDisplay) &&
                !(e instanceof Display.ItemDisplay) &&
                !(e instanceof Display.TextDisplay) &&
                !(e instanceof ShulkerBullet) && !(e instanceof PrimedTnt) &&
                !(e instanceof EyeOfEnder) && !(e instanceof LeashFenceKnotEntity);
    }

    private static double getFov(Camera camInfo, float partialTicks, boolean useSetting) {
        if (mc.gameRenderer.isPanoramicMode()) {
            return 90.0F;
        }
        double fov = useSetting ? mc.options.fov().get() : 70.0F;
        if (camInfo.getEntity() instanceof LivingEntity le && le.isDeadOrDying()) {
            float deathTime = Math.min(le.deathTime + partialTicks, 20f);
            fov /= ((1f - 500f/(deathTime+500f))*2f + 1f);
        }
        FogType fog = camInfo.getFluidInCamera();
        if (fog == FogType.LAVA || fog == FogType.WATER) {
            fov *= Mth.lerp(mc.options.fovEffectScale().get(), 1f, 0.85714287f);
        }
        return ClientHooks.getFieldOfView(mc.gameRenderer, camInfo, partialTicks, fov, useSetting);
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientTick(ClientTickEvent.Post event) {
        refreshCombinedList();
    }

    private static void refreshCombinedList() {
        combinedEntities.clear();
        if (mc.player == null || mc.level == null) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 pos = cam.getPosition();

        double hw = TEXTURE_WIDTH/2.0, hh = TEXTURE_HEIGHT/2.0, hd = TEXTURE_DEPTH/2.0;
        AABB bounds = new AABB(
                pos.x - hw, pos.y - hh, pos.z - hd,
                pos.x + hw, pos.y + hh, pos.z + hd
        );

        // collect & categorize
        List<Entity> near = new ArrayList<>();
        List<Entity> med  = new ArrayList<>();
        List<Entity> far  = new ArrayList<>();

        mc.level.getEntities(null, bounds).stream()
                .filter(TrailTextureManager::emitsHeat)
                .forEach(e -> {
                    double d = Math.sqrt(e.distanceToSqr(pos));
                    if (d < NEARBY_DISTANCE) near.add(e);
                    else if (d < MEDIUM_DISTANCE) med.add(e);
                    else far.add(e);
                });

        combinedEntities.addAll(near);
        combinedEntities.addAll(med);
        combinedEntities.addAll(far);
        // roundRobinIndex is NOT reset
        if (roundRobinIndex >= combinedEntities.size()) {
            roundRobinIndex = 0;
        }
    }

    public static void bindForRender(ShaderInstance shader, RenderType rt, boolean useDelta) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();

        // build frustum
        Quaternionf q = cam.rotation().conjugate(new Quaternionf());
        Matrix4f view = new Matrix4f().rotation(q);
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        double fov = getFov(cam, partial, true) * 1.5f;
        Frustum frustum = new Frustum(view, mc.gameRenderer.getProjectionMatrix(Math.max(fov, mc.options.fov().get())));
        frustum.prepare(camPos.x, camPos.y, camPos.z);

        // round‐robin select
        FloatBuffer buf = FloatBuffer.allocate(DATA_ARRAY_SIZE);
        int sent = 0, checked = 0;
        int total = combinedEntities.size();

        while (sent < MAX_ENTITIES && checked < total) {
            Entity e = combinedEntities.get(roundRobinIndex);
            roundRobinIndex = (roundRobinIndex + 1) % total;
            checked++;
            if (!isVisible(e, frustum)) continue;
            Vec3 p = e.position();
            buf.put((float)(p.x - camPos.x))
                    .put((float)(p.y - camPos.y + e.getBbHeight()/2f))
                    .put((float)(p.z - camPos.z));
            sent++;
        }
        // zero‐pad rest
        while (buf.position() < DATA_ARRAY_SIZE) {
            buf.put(0f);
        }

        // upload uniforms
        var uCount = shader.getUniform("EntityCount");
        if (uCount != null) { uCount.set(sent); uCount.upload(); }
        var uData = shader.getUniform("EntityData");
        if (uData != null) { uData.set(buf.array()); uData.upload(); }
        var uCam  = shader.getUniform("CameraPos");
        if (uCam != null) {
            uCam.set((float)camPos.x, (float)camPos.y, (float)camPos.z);
            uCam.upload();
        }
        var uDt = shader.getUniform("u_deltaTime");
        if (uDt != null) {
            uDt.set(useDelta ? getDeltaTimeFor(rt) : 0.01f);
            uDt.upload();
        }
    }

    private static boolean isVisible(Entity e, Frustum f) {
        return f.isVisible(e.getBoundingBox().inflate(0.1));
    }

    public static void unbindAfterRender() { }

    public static void cleanup() {
        lastCallTimes.clear();
        cachedDeltaTimes.clear();
        combinedEntities.clear();
        roundRobinIndex = 0;
    }
}
