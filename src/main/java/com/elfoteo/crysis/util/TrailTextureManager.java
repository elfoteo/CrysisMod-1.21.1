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
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;

/**
 * Manages a single 512×512×384 R16 3D "trail" texture in world‐space.
 * Handles allocation, compute‐shader setup, shifting, world‐offset tracking,
 * and (now) binding/unbinding for rendering.
 * This is a static utility class - all methods and fields are static.
 */
@EventBusSubscriber(modid = CrysisMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class TrailTextureManager {
    // ---------------------------------------------------------
    // Texture IDs and size
    // ---------------------------------------------------------
    private static int trailTextureId     = -1;
    private static int tempTrailTextureId = -1;
    private static final int trailTexWidth  = 512;
    private static final int trailTexHeight = 512;
    private static final int trailTexDepth  = 384;

    // The origin (in blocks) of the "(0,0,0)" texel:
    private static int worldOffsetX = 0;
    private static int worldOffsetY = 0;
    private static int worldOffsetZ = 0;

    // Has the shader / texture been initialized yet this session?
    private static boolean textureInitialized = false;
    private static final Minecraft mc = Minecraft.getInstance();

    // ---------------------------------------------------------
    // Compute‐shader objects
    // ---------------------------------------------------------
    private static int shiftComputeShader  = -1;
    private static int shiftComputeProgram = -1;

    // ---------------------------------------------------------
    // Timing (per‐RenderType)
    // ---------------------------------------------------------
    private static final Map<Object /* RenderType */, Long> lastCallTimes    = new HashMap<>();
    private static final Map<Object /* RenderType */, Float> cachedDeltaTimes = new HashMap<>();

    // ---------------------------------------------------------
    // Saved GL state (for binding/unbinding)
    // ---------------------------------------------------------
    private static int prevActiveTextureUnit = GL13.GL_TEXTURE0;
    private static int prevTex2D             = 0;
    private static int prevTex3D             = 0;

    // Private constructor to prevent instantiation
    private TrailTextureManager() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // ----------------------------------------------------------------
    // PUBLIC API: getters for mixin to upload uniforms
    // ----------------------------------------------------------------

    /** Current world‐space X offset (in blocks) that corresponds to texel (0,*,*). */
    public static int getWorldOffsetX() {
        return worldOffsetX;
    }

    /** Current world‐space Y offset (in blocks) that corresponds to texel (*,0,*). */
    public static int getWorldOffsetY() {
        return worldOffsetY;
    }

    /** Current world‐space Z offset (in blocks) that corresponds to texel (*,*,0). */
    public static int getWorldOffsetZ() {
        return worldOffsetZ;
    }

    /**
     * Returns the delta time (in seconds) since the last call for this RenderType.
     * The first time this is called for a given RenderType, returns 0.0f.
     */
    public static float getDeltaTimeFor(Object renderType) {
        long currentTime = System.nanoTime();
        Long lastTime = lastCallTimes.get(renderType);

        if (lastTime == null) {
            lastCallTimes.put(renderType, currentTime);
            cachedDeltaTimes.put(renderType, 0.0f);
            return 0.0f;
        }

        float deltaSeconds = (currentTime - lastTime) / 1_000_000_000.0f;
        lastCallTimes.put(renderType, currentTime);
        cachedDeltaTimes.put(renderType, deltaSeconds);
        return deltaSeconds;
    }

    /**
     * Ensure the 3D trail texture exists (and is cleared). Also initializes
     * worldOffsetX/Y/Z so that the camera starts centered in a 512×512×384 region.
     * Must be called once before you attempt to shift or render.
     */
    public static void allocateOrResizeIfNeeded() {
        // If already allocated and window size hasn't changed, do nothing.
        if (trailTextureId != -1) {
            return;
        }

        // If an old texture exists, delete it so we start fresh.
        if (trailTextureId != -1) {
            GL11.glDeleteTextures(trailTextureId);
            trailTextureId = -1;
        }
        if (tempTrailTextureId != -1) {
            GL11.glDeleteTextures(tempTrailTextureId);
            tempTrailTextureId = -1;
        }

        // -------------------------------------------------------------
        // Save old binding of TEXTURE_3D
        // -------------------------------------------------------------
        prevTex3D = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);

        // -------------------------------------------------------------
        // Allocate new 512×512×384 R16 3D texture
        // -------------------------------------------------------------
        trailTextureId = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, trailTextureId);

        GL12.glTexImage3D(
                GL12.GL_TEXTURE_3D,
                0,
                GL30.GL_R16,            // single‐channel 16‐bit
                trailTexWidth,
                trailTexHeight,
                trailTexDepth,
                0,
                GL11.GL_RED,            // read/write as red channel
                GL11.GL_UNSIGNED_SHORT, // 16-bit data
                (ByteBuffer) null       // null → zero‐initialized (if GL4.4 is missing)
        );
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);

        // Attempt to clear to zero using glClearTexImage (requires GL4.4+)
        try {
            GL44.glClearTexImage(trailTextureId, 0, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, (ByteBuffer)null);
        } catch (Throwable e) {
            // If it fails, we trust that the null upload zero‐initialized it anyway.
        }

        // -------------------------------------------------------------
        // Restore previous TEXTURE_3D binding
        // -------------------------------------------------------------
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, prevTex3D);

        // -------------------------------------------------------------
        // Compute initial worldOffset so the camera is at the center
        // -------------------------------------------------------------
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int camBlockX = (int) Math.floor(camPos.x);
        int camBlockY = (int) Math.floor(camPos.y);
        int camBlockZ = (int) Math.floor(camPos.z);

        worldOffsetX = camBlockX - (trailTexWidth  / 2);  // 256 = 512/2
        worldOffsetY = camBlockY - (trailTexDepth  / 2);  // 192 = 384/2
        worldOffsetZ = camBlockZ - (trailTexHeight / 2);  // 256 = 512/2

        textureInitialized = false;

        // Finally, initialize the compute shader.
        initializeComputeShader();
    }

    /**
     * Call every frame (before rendering) to see if we need to "shift" the texture
     * based on how far the camera moved. If so, we dispatch the compute shader
     * to shift content, swap out the main texture, and update worldOffsetX/Y/Z.
     */
    public static void updateTexturePosition(Vec3 currentCameraPos) {
        if (!textureInitialized) {
            // On first call, we do not shift—just mark as initialized.
            textureInitialized = true;
            return;
        }

        // Compute where the center of our 512×512×384 region is, in block coords:
        int textureCenterX = worldOffsetX + (trailTexWidth  / 2);
        int textureCenterY = worldOffsetY + (trailTexDepth  / 2);
        int textureCenterZ = worldOffsetZ + (trailTexHeight / 2);

        int camBlockX = (int) Math.floor(currentCameraPos.x);
        int camBlockY = (int) Math.floor(currentCameraPos.y);
        int camBlockZ = (int) Math.floor(currentCameraPos.z);

        int deltaX = camBlockX - textureCenterX;
        int deltaY = camBlockY - textureCenterY;
        int deltaZ = camBlockZ - textureCenterZ;

        final int SHIFT_THRESHOLD = 64;
        int shiftX = 0, shiftY = 0, shiftZ = 0;
        boolean needsShift = false;

        if (Math.abs(deltaX) > SHIFT_THRESHOLD) {
            shiftX = (deltaX > 0 ? 64 : -64);
            needsShift = true;
        }
        if (Math.abs(deltaY) > SHIFT_THRESHOLD) {
            shiftY = (deltaY > 0 ? 64 : -64);
            needsShift = true;
        }
        if (Math.abs(deltaZ) > SHIFT_THRESHOLD) {
            shiftZ = (deltaZ > 0 ? 64 : -64);
            needsShift = true;
        }

        if (needsShift) {
            // Note: we supply −shiftX etc. to move texels "toward" the camera.
            shiftTextureContent(-shiftX, -shiftY, -shiftZ);

            // Now update world‐offset to reflect that the "window" has moved.
            worldOffsetX += shiftX;
            worldOffsetY += shiftY;
            worldOffsetZ += shiftZ;
        }
    }

    // ----------------------------------------------------------------
    // PRIVATE: compute‐shader initialization + shifting logic
    // ----------------------------------------------------------------

    private static void initializeComputeShader() {
        if (shiftComputeProgram != -1) return; // already done

        String computeShaderSource = """
            #version 430
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;

            layout(binding = 0, r16) uniform image3D srcTexture;
            layout(binding = 1, r16) uniform image3D dstTexture;

            uniform ivec3 shiftOffset;
            uniform ivec3 textureSize;

            void main() {
                ivec3 dstCoord = ivec3(gl_GlobalInvocationID);

                if (any(greaterThanEqual(dstCoord, textureSize))) {
                    return;
                }

                ivec3 srcCoord = dstCoord - shiftOffset;
                float value = 0.0;
                if (all(greaterThanEqual(srcCoord, ivec3(0))) &&
                    all(lessThan(srcCoord, textureSize))) {
                    value = imageLoad(srcTexture, srcCoord).r;
                }
                imageStore(dstTexture, dstCoord, vec4(value, 0.0, 0.0, 0.0));
            }
            """;

        // 1. Compile compute shader
        shiftComputeShader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(shiftComputeShader, computeShaderSource);
        GL20.glCompileShader(shiftComputeShader);

        if (GL20.glGetShaderi(shiftComputeShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shiftComputeShader);
            throw new RuntimeException("Compute shader compilation failed: " + log);
        }

        // 2. Link into a program
        shiftComputeProgram = GL20.glCreateProgram();
        GL20.glAttachShader(shiftComputeProgram, shiftComputeShader);
        GL20.glLinkProgram(shiftComputeProgram);

        if (GL20.glGetProgrami(shiftComputeProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(shiftComputeProgram);
            throw new RuntimeException("Compute shader linking failed: " + log);
        }
    }

    private static void shiftTextureContent(int deltaX, int deltaY, int deltaZ) {
        if (shiftComputeProgram == -1) {
            System.err.println("Compute shader not initialized—cannot shift texture.");
            return;
        }

        // Save current GL state
        int prevProgram    = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevTex3D_0    = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);

        try {
            // If our "temp" texture doesn't exist yet, create it now
            if (tempTrailTextureId == -1) {
                tempTrailTextureId = GL11.glGenTextures();
                GL11.glBindTexture(GL12.GL_TEXTURE_3D, tempTrailTextureId);
                GL12.glTexImage3D(
                        GL12.GL_TEXTURE_3D, 0, GL30.GL_R16,
                        trailTexWidth, trailTexHeight, trailTexDepth,
                        0, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, (ByteBuffer)null
                );
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
            }

            // 1. Use compute shader
            GL20.glUseProgram(shiftComputeProgram);

            // 2. Bind "old" texture to unit 0 (read) and "temp" to unit 1 (write)
            GL42.glBindImageTexture(
                    0,
                    trailTextureId,
                    0,
                    true,
                    0,
                    GL15.GL_READ_ONLY,
                    GL30.GL_R16
            );
            GL42.glBindImageTexture(
                    1,
                    tempTrailTextureId,
                    0,
                    true,
                    0,
                    GL15.GL_WRITE_ONLY,
                    GL30.GL_R16
            );

            // 3. Upload uniforms: shiftOffset and textureSize
            int shiftOffsetLoc = GL20.glGetUniformLocation(shiftComputeProgram, "shiftOffset");
            int textureSizeLoc = GL20.glGetUniformLocation(shiftComputeProgram, "textureSize");
            if (shiftOffsetLoc != -1) {
                GL20.glUniform3i(shiftOffsetLoc, deltaX, deltaY, deltaZ);
            }
            if (textureSizeLoc != -1) {
                GL20.glUniform3i(textureSizeLoc, trailTexWidth, trailTexHeight, trailTexDepth);
            }

            // 4. Dispatch compute
            int groupsX = (trailTexWidth  + 7) / 8;
            int groupsY = (trailTexHeight + 7) / 8;
            int groupsZ = (trailTexDepth  + 7) / 8;
            GL43.glDispatchCompute(groupsX, groupsY, groupsZ);

            // 5. Memory barrier so writes are visible
            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

            // 6. Swap texture IDs (temp becomes main, main becomes temp)
            int tmp = trailTextureId;
            trailTextureId = tempTrailTextureId;
            tempTrailTextureId = tmp;
        } catch (Exception e) {
            System.err.println("Error during texture shifting: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Restore previous GL state
            GL20.glUseProgram(prevProgram);
            GL11.glBindTexture(GL12.GL_TEXTURE_BINDING_3D, prevTex3D_0);

            // Unbind image units 0 and 1
            GL42.glBindImageTexture(0, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R16);
            GL42.glBindImageTexture(1, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R16);
        }
    }

    // ---------------------------------------------------------
    // INTERNAL: expose texture ID so that mixin can bind it
    // ---------------------------------------------------------
    public static int getTrailTextureId() {
        return trailTextureId;
    }

    public static boolean emitsHeat(Entity e){
        return !(e instanceof Arrow) && !(e instanceof SpectralArrow) && !(e instanceof SnowGolem) && !(e instanceof ExperienceOrb) && !(e instanceof ItemEntity) && !(e instanceof ThrownPotion) && !(e instanceof ThrownTrident) && !(e instanceof ThrownEnderpearl) && !(e instanceof ThrownEgg) && !(e instanceof Snowball) && !(e instanceof ThrownExperienceBottle) && !(e instanceof SmallFireball) && !(e instanceof WitherSkull) && !(e instanceof EvokerFangs) && !(e instanceof AreaEffectCloud) && !(e instanceof LightningBolt) && !(e instanceof FishingHook) && !(e instanceof AbstractMinecart) && !(e instanceof Boat) && !(e instanceof Painting) && !(e instanceof ItemFrame) && !(e instanceof ArmorStand) && !(e instanceof Marker) && !(e instanceof EndCrystal) && !(e instanceof FireworkRocketEntity) && !(e instanceof Display.BlockDisplay) && !(e instanceof Display.ItemDisplay) && !(e instanceof Display.TextDisplay) && !(e instanceof ShulkerBullet) && !(e instanceof PrimedTnt) && !(e instanceof EyeOfEnder) && !(e instanceof LeashFenceKnotEntity);
    }

    private static double getFov(Camera activeRenderInfo, float partialTicks, boolean useFOVSetting) {
        if (mc.gameRenderer.isPanoramicMode()) {
            return 90.0F;
        } else {
            double d0 = 70.0F;
            if (useFOVSetting) {
                d0 = (double) mc.options.fov().get();
            }

            if (activeRenderInfo.getEntity() instanceof LivingEntity && ((LivingEntity)activeRenderInfo.getEntity()).isDeadOrDying()) {
                float f = Math.min((float)((LivingEntity)activeRenderInfo.getEntity()).deathTime + partialTicks, 20.0F);
                d0 /= ((1.0F - 500.0F / (f + 500.0F)) * 2.0F + 1.0F);
            }

            FogType fogtype = activeRenderInfo.getFluidInCamera();
            if (fogtype == FogType.LAVA || fogtype == FogType.WATER) {
                d0 *= Mth.lerp(mc.options.fovEffectScale().get(), 1.0F, (double)0.85714287F);
            }

            return ClientHooks.getFieldOfView(mc.gameRenderer, activeRenderInfo, (double)partialTicks, d0, useFOVSetting);
        }
    }

    private static final int MAX_PER_FRAME = 32;             // 32 uploads per frame
    private static final float TICK_RATE = 20f;             // ticks per second (20 Hz)
    private static final float UPDATES_PER_SEC = MAX_PER_FRAME * TICK_RATE; // 640 updates/sec

    // Texture dimensions
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 512;
    private static final int TEXTURE_DEPTH = 384;

    // Priority distance thresholds (simple ranges)
    private static final double NEARBY_DISTANCE = 64.0;   // < 64 blocks
    private static final double MEDIUM_DISTANCE = 128.0;  // 64-128 blocks
// > 128 blocks = far

    // Entity queues by priority
    private static final List<Entity> nearbyEntities = new ArrayList<>();
    private static final List<Entity> mediumEntities = new ArrayList<>();
    private static final List<Entity> farEntities = new ArrayList<>();

    // Current processing indices for each priority queue
    private static int nearbyIndex = 0;
    private static int mediumIndex = 0;
    private static int farIndex = 0;

    // accumulator of how many entity-uploads we may spend
    private static float updateBudget = 0f;

    // Called at 20 Hz - refresh entity lists
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientTick(ClientTickEvent.Post event) {
        // Each tick you gain MAX_PER_TICK units of budget:
        updateBudget += MAX_PER_FRAME;
        // clamp to avoid runaway if lagged:
        if (updateBudget > UPDATES_PER_SEC) {
            updateBudget = UPDATES_PER_SEC;
        }

        // Refresh entity lists every tick
        refreshEntityLists();
    }

    /**
     * Pull all entities within texture bounds and sort into priority groups
     */
    private static void refreshEntityLists() {
        if (mc.player == null || mc.level == null) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();

        // Clear previous lists
        nearbyEntities.clear();
        mediumEntities.clear();
        farEntities.clear();

        // Reset indices when lists are refreshed
        nearbyIndex = 0;
        mediumIndex = 0;
        farIndex = 0;

        // Calculate texture bounds (centered on camera)
        double halfWidth = TEXTURE_WIDTH / 2.0;
        double halfHeight = TEXTURE_HEIGHT / 2.0;
        double halfDepth = TEXTURE_DEPTH / 2.0;

        AABB textureBox = new AABB(
                camPos.x - halfWidth, camPos.y - halfHeight, camPos.z - halfDepth,
                camPos.x + halfWidth, camPos.y + halfHeight, camPos.z + halfDepth
        );

        // Get all entities within texture bounds
        List<Entity> allEntities = mc.level.getEntities(null, textureBox).stream()
                .filter(TrailTextureManager::emitsHeat)
                .toList();

        // Sort into priority groups based on distance
        for (Entity entity : allEntities) {
            double distanceSq = entity.distanceToSqr(camPos);
            double distance = Math.sqrt(distanceSq);

            if (distance < NEARBY_DISTANCE) {
                nearbyEntities.add(entity);
            } else if (distance < MEDIUM_DISTANCE) {
                mediumEntities.add(entity);
            } else {
                farEntities.add(entity);
            }
        }
    }

    /**
     * Get next batch of entities to process this frame, prioritizing nearby > medium > far
     */
    private static List<Entity> getNextEntityBatch(int maxCount, Frustum frustum) {
        List<Entity> batch = new ArrayList<>();

        // First, try to fill from nearby entities
        while (batch.size() < maxCount && nearbyIndex < nearbyEntities.size()) {
            Entity entity = nearbyEntities.get(nearbyIndex++);
            if (isEntityVisible(entity, frustum)) {
                batch.add(entity);
            }
        }

        // Then medium entities
        while (batch.size() < maxCount && mediumIndex < mediumEntities.size()) {
            Entity entity = mediumEntities.get(mediumIndex++);
            if (isEntityVisible(entity, frustum)) {
                batch.add(entity);
            }
        }

        // Finally far entities
        while (batch.size() < maxCount && farIndex < farEntities.size()) {
            Entity entity = farEntities.get(farIndex++);
            if (isEntityVisible(entity, frustum)) {
                batch.add(entity);
            }
        }

        return batch;
    }

    /**
     * Simple visibility check - only render visible entities
     */
    private static boolean isEntityVisible(Entity entity, Frustum frustum) {
        AABB bb = entity.getBoundingBox().inflate(0.1);
        return frustum.isVisible(bb);
    }

    /**
     * Saves the current GL state (active texture unit, bound 2D, bound 3D),
     * then binds the trailTextureId as image unit 1 (read/write) and as sampler 2,
     * and uploads the "TrailSampler" uniform to point at sampler unit 2.
     */
    public static void bindForRender(ShaderInstance shaderInstance, RenderType renderType, boolean useDelta) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();

        // Build view frustum
        Quaternionf quaternionf = cam.rotation().conjugate(new Quaternionf());
        Matrix4f matrix4f1 = new Matrix4f().rotation(quaternionf);
        float f = TrailTextureManager.mc.getTimer().getGameTimeDeltaPartialTick(true);
        double d0 = getFov(cam, f, true);
        Frustum frustum = new Frustum(matrix4f1, mc.gameRenderer.getProjectionMatrix(Math.max(d0, TrailTextureManager.mc.options.fov().get())));
        frustum.prepare(camPos.x, camPos.y, camPos.z);

        // Figure out how many uploads we can spend this frame:
        // gain fractional budget proportional to frame time
        float frameTime = mc.getTimer().getRealtimeDeltaTicks(); // seconds since last frame
        updateBudget += UPDATES_PER_SEC * frameTime - 0; // (adds ~10.7 per 1/60s)
        int budget = (int) Math.floor(updateBudget);
        updateBudget -= budget;

        // Limit budget to MAX_PER_FRAME
        budget = Math.min(budget, MAX_PER_FRAME);

        // Get next batch of entities to process (up to budget limit)
        List<Entity> entitiesToProcess = getNextEntityBatch(budget, frustum);

        // Fill buffer with prioritized entities
        FloatBuffer buf = FloatBuffer.allocate(64 * 3);
        for (Entity e : entitiesToProcess) {
            Vec3 p = e.position();
            float ex = (float)(p.x - camPos.x);
            float ey = (float)(p.y - camPos.y + e.getBbHeight()/2f);
            float ez = (float)(p.z - camPos.z);

            buf.put(ex).put(ey).put(ez);
        }
        int count = entitiesToProcess.size();

        var ec = shaderInstance.getUniform("EntityCount");
        if (ec != null) {
            ec.set(count);
            ec.upload();
        }
        var ed = shaderInstance.getUniform("EntityData");
        if (ed != null) {
            ed.set(buf.array());
            ed.upload();
        }

        var cwp = shaderInstance.getUniform("CameraPos");
        if (cwp != null) {
            Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
            cwp.set((float) c.x, (float) c.y, (float) c.z);
            cwp.upload();
        }

        // ———————————————————————————————
        // 6) Upload world offsets from TrailTextureManager
        // ———————————————————————————————
        var uOffX = shaderInstance.getUniform("u_worldOffsetX");
        if (uOffX != null) {
            uOffX.set(getWorldOffsetX());
            uOffX.upload();
        }
        var uOffY = shaderInstance.getUniform("u_worldOffsetY");
        if (uOffY != null) {
            uOffY.set(getWorldOffsetY());
            uOffY.upload();
        }
        var uOffZ = shaderInstance.getUniform("u_worldOffsetZ");
        if (uOffZ != null) {
            uOffZ.set(getWorldOffsetZ());
            uOffZ.upload();
        }

        // ———————————————————————————————
        // 7) Upload deltaTime uniform
        // ———————————————————————————————
        var dt = shaderInstance.getUniform("u_deltaTime");
        if (dt != null) {
            dt.set(useDelta? getDeltaTimeFor(renderType): 0.01f);
            dt.upload();
        }

        // 1) Save the old active texture unit:
        prevActiveTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        // 2) Save the old TEXTURE_2D binding (unit 0) and TEXTURE_3D binding (unit 2):
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        prevTex2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        prevTex3D = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);

        // 3) Bind our 3D texture to image unit 1 for read/write
        GL42.glBindImageTexture(
                1,
                trailTextureId,
                0,
                true,
                0,
                GL15.GL_READ_WRITE,
                GL30.GL_R16
        );

        // 4) Also bind it to sampler unit 2
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, trailTextureId);

        // 5) Upload the "TrailSampler" uniform (so the shader samples from unit 2)
        var trailSampler = shaderInstance.getUniform("TrailSampler");
        if (trailSampler != null) {
            trailSampler.set(2);
            trailSampler.upload();
        }

        // 6) Restore active texture unit back to whatever it was before:
        GL13.glActiveTexture(prevActiveTextureUnit);
    }

    /**
     * Unbinds the image unit 1 (so it no longer points at trailTextureId),
     * restores the old TEXTURE_3D binding on unit 2 and the old TEXTURE_2D on unit 0,
     * and then re‐activates whatever texture unit was active before.
     */
    public static void unbindAfterRender() {
        // 1) Unbind image unit 1:
        GL42.glBindImageTexture(
                1,
                0,
                0,
                false,
                0,
                GL15.GL_READ_WRITE,
                GL30.GL_R16
        );

        // 2) Restore the old sampler binding (unit 2):
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, prevTex3D);

        // 3) Restore the old 2D texture binding (unit 0):
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex2D);

        // 4) Finally, restore whichever texture unit was active before bindForRender():
        GL13.glActiveTexture(prevActiveTextureUnit);
    }

    /**
     * Cleanup method to properly dispose of OpenGL resources.
     * Should be called when shutting down or when the class is no longer needed.
     */
    public static void cleanup() {
        if (trailTextureId != -1) {
            GL11.glDeleteTextures(trailTextureId);
            trailTextureId = -1;
        }
        if (tempTrailTextureId != -1) {
            GL11.glDeleteTextures(tempTrailTextureId);
            tempTrailTextureId = -1;
        }
        if (shiftComputeShader != -1) {
            GL20.glDeleteShader(shiftComputeShader);
            shiftComputeShader = -1;
        }
        if (shiftComputeProgram != -1) {
            GL20.glDeleteProgram(shiftComputeProgram);
            shiftComputeProgram = -1;
        }

        // Clear timing maps
        lastCallTimes.clear();
        cachedDeltaTimes.clear();

        // Reset state
        textureInitialized = false;
        worldOffsetX = 0;
        worldOffsetY = 0;
        worldOffsetZ = 0;
    }
}

