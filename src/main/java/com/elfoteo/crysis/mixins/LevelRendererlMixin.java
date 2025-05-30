package com.elfoteo.crysis.mixins;

import com.elfoteo.crysis.gui.util.EntityDisposition;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SetSectionRenderDispatcher;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class LevelRendererlMixin implements SetSectionRenderDispatcher {

    @Shadow @Nullable private VertexBuffer skyBuffer;

    @Shadow @Final private Minecraft minecraft;

    @Shadow private double xTransparentOld;

    @Shadow private double yTransparentOld;

    @Shadow private double zTransparentOld;

    @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Shadow @Nullable private SectionRenderDispatcher sectionRenderDispatcher;

    @Shadow public abstract Frustum getFrustum();

    @Shadow private int ticks;

    @Shadow @Final private RenderBuffers renderBuffers;

    @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void renderOnlySkyColor(
            Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera,
            boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {

        if (Nanosuit.currentClientMode != SuitModes.VISOR.get()) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;

        if (level == null) return;

        // Set up
        skyFogSetup.run();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(frustumMatrix);

        float r = 0f;
        float g = 0f;
        float b = 65f/255;

        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(r, g, b, 1.0F);

        ShaderInstance shader = RenderSystem.getShader();

        skyBuffer.bind();
        skyBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
        VertexBuffer.unbind();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1.0F);

        RenderSystem.depthMask(true);

        ci.cancel(); // Cancel vanilla method to prevent further rendering
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void renderClouds(
            PoseStack poseStack, Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        ci.cancel(); // Cancel vanilla method to prevent further rendering
    }

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void renderEntity(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        double d0 = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double d1 = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double d2 = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        float f = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        if (Nanosuit.currentClientMode == SuitModes.VISOR.get()){
            if (bufferSource instanceof OutlineBufferSource obs){
                int packedColor = EntityDisposition.getColor(entity);
                obs.setColor(FastColor.ARGB32.red(packedColor), FastColor.ARGB32.green(packedColor), FastColor.ARGB32.blue(packedColor), 255);
            }
        }
        this.entityRenderDispatcher.render(entity, d0 - camX, d1 - camY, d2 - camZ, f, partialTick, poseStack, bufferSource, this.entityRenderDispatcher.getPackedLightCoords(entity, partialTick));
        ci.cancel();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Single "world‐space" 3D trail texture at 1:1 resolution per block:
    @Unique private int trailTextureId     = -1;
    @Unique private int trailFramebufferId = -1;
    @Unique private int lastWindowWidth    = -1;
    @Unique private int lastWindowHeight   = -1;

    // Cover a 512×512×384‐block region at 1×1×1 per block → 512×512×384 texture
    @Unique private final int trailTexWidth  = 512;
    @Unique private final int trailTexHeight = 512;
    @Unique private final int trailTexDepth  = 384;

    // Which block X/Y/Z corresponds to texture coordinate (0,0,0)? (set per frame)
    @Unique private int worldOffsetX = 0;
    @Unique private int worldOffsetY = 0;
    @Unique private int worldOffsetZ = 0;

    // Track last camera position for shift detection
    @Unique private Vec3 lastCameraPos = Vec3.ZERO;
    @Unique private boolean textureInitialized = false;

    // Compute shader for texture shifting
    @Unique private int shiftComputeShader = -1;
    @Unique private int shiftComputeProgram = -1;

    // Temporary texture for shifting operations
    @Unique private int tempTrailTextureId = -1;

    /**
     * Initialize the compute shader for texture shifting
     */
    @Unique
    private void initializeComputeShader() {
        if (shiftComputeProgram != -1) return; // Already initialized

        String computeShaderSource = """
        #version 430
        layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
        
        layout(binding = 0, r16) uniform image3D srcTexture;
        layout(binding = 1, r16) uniform image3D dstTexture;
        
        uniform ivec3 shiftOffset;
        uniform ivec3 textureSize;
        
        void main() {
            ivec3 dstCoord = ivec3(gl_GlobalInvocationID);
            
            // Check bounds for destination
            if (any(greaterThanEqual(dstCoord, textureSize))) {
                return;
            }
            
            // Calculate source coordinate: where to read from in the old texture
            // If shiftOffset is positive, we're moving content in positive direction
            // So to fill position dstCoord, we need to read from dstCoord - shiftOffset
            ivec3 srcCoord = dstCoord - shiftOffset;
            
            float value = 0.0;
            
            // Only read if source coordinate is within bounds, otherwise use 0 (empty)
            if (all(greaterThanEqual(srcCoord, ivec3(0))) && 
                all(lessThan(srcCoord, textureSize))) {
                value = imageLoad(srcTexture, srcCoord).r;
            }
            
            // Store the value (either copied or 0 for new areas)
            imageStore(dstTexture, dstCoord, vec4(value, 0.0, 0.0, 0.0));
        }
        """;

        try {
            // Create and compile compute shader
            shiftComputeShader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
            GL20.glShaderSource(shiftComputeShader, computeShaderSource);
            GL20.glCompileShader(shiftComputeShader);

            // Check compilation status
            if (GL20.glGetShaderi(shiftComputeShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetShaderInfoLog(shiftComputeShader);
                throw new RuntimeException("Compute shader compilation failed: " + log);
            }

            // Create and link program
            shiftComputeProgram = GL20.glCreateProgram();
            GL20.glAttachShader(shiftComputeProgram, shiftComputeShader);
            GL20.glLinkProgram(shiftComputeProgram);

            // Check linking status
            if (GL20.glGetProgrami(shiftComputeProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(shiftComputeProgram);
                throw new RuntimeException("Compute shader linking failed: " + log);
            }

            System.out.println("Compute shader initialized successfully");

        } catch (Exception e) {
            System.err.println("Failed to initialize compute shader: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Shift the 3D texture content by the given offset using compute shader
     */
    @Unique
    private void shiftTextureContent(int deltaX, int deltaY, int deltaZ) {
        if (shiftComputeProgram == -1) {
            System.err.println("Compute shader not initialized, cannot shift texture");
            return;
        }

        // Save current GL state
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevTex3D_0 = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);

        try {
            // Create temporary texture if it doesn't exist
            if (tempTrailTextureId == -1) {
                tempTrailTextureId = GL11.glGenTextures();
                GL11.glBindTexture(GL12.GL_TEXTURE_3D, tempTrailTextureId);

                GL12.glTexImage3D(
                        GL12.GL_TEXTURE_3D, 0, GL30.GL_R16,
                        trailTexWidth, trailTexHeight, trailTexDepth,
                        0, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, (ByteBuffer) null
                );

                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
            }

            // Use compute shader
            GL20.glUseProgram(shiftComputeProgram);

            // Bind textures as images
            GL42.glBindImageTexture(0, trailTextureId, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R16);
            GL42.glBindImageTexture(1, tempTrailTextureId, 0, true, 0, GL15.GL_WRITE_ONLY, GL30.GL_R16);

            // Set uniforms
            int shiftOffsetLoc = GL20.glGetUniformLocation(shiftComputeProgram, "shiftOffset");
            int textureSizeLoc = GL20.glGetUniformLocation(shiftComputeProgram, "textureSize");

            if (shiftOffsetLoc != -1) {
                GL20.glUniform3i(shiftOffsetLoc, deltaX, deltaY, deltaZ);
            }
            if (textureSizeLoc != -1) {
                GL20.glUniform3i(textureSizeLoc, trailTexWidth, trailTexHeight, trailTexDepth);
            }

            // Dispatch compute shader
            int groupsX = (trailTexWidth + 7) / 8;
            int groupsY = (trailTexHeight + 7) / 8;
            int groupsZ = (trailTexDepth + 7) / 8;
            GL43.glDispatchCompute(groupsX, groupsY, groupsZ);

            // Wait for completion
            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

            // Swap textures (make temp the new main texture)
            int temp = trailTextureId;
            trailTextureId = tempTrailTextureId;
            tempTrailTextureId = temp;

        } catch (Exception e) {
            System.err.println("Error during texture shifting: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Restore GL state
            GL20.glUseProgram(prevProgram);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, prevTex3D_0);

            // Unbind image units
            GL42.glBindImageTexture(0, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R16);
            GL42.glBindImageTexture(1, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R16);
        }
    }

    /**
     * Check if camera has moved significantly and shift texture if needed
     */
    @Unique
    private void updateTexturePosition(Vec3 currentCameraPos) {
        if (!textureInitialized) {
            lastCameraPos = currentCameraPos;
            textureInitialized = true;
            return;
        }

        // Calculate the current center of our texture in world coordinates
        int textureCenterX = worldOffsetX + (trailTexWidth / 2);
        int textureCenterY = worldOffsetY + (trailTexDepth / 2);
        int textureCenterZ = worldOffsetZ + (trailTexHeight / 2);

        // Current camera position in block coordinates (with +64 offset like in shader)
        int camBlockX = (int) Math.floor(currentCameraPos.x + 64);
        int camBlockY = (int) Math.floor(currentCameraPos.y + 64);
        int camBlockZ = (int) Math.floor(currentCameraPos.z + 64);

        // Calculate how far the camera is from the texture center
        int deltaX = camBlockX - textureCenterX;
        int deltaY = camBlockY - textureCenterY;
        int deltaZ = camBlockZ - textureCenterZ;

        // Define threshold for shifting (shift when moved 64 blocks from center)
        final int SHIFT_THRESHOLD = 64;

        int shiftX = 0, shiftY = 0, shiftZ = 0;
        boolean needsShift = false;

        // Calculate how much to shift to recenter the texture
        if (Math.abs(deltaX) > SHIFT_THRESHOLD) {
            // Shift in chunks of 64 blocks toward the camera
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
            System.out.println("Camera at: " + camBlockX + ", " + camBlockY + ", " + camBlockZ);
            System.out.println("Texture center at: " + textureCenterX + ", " + textureCenterY + ", " + textureCenterZ);
            System.out.println("Delta: " + deltaX + ", " + deltaY + ", " + deltaZ);
            System.out.println("Shifting texture by: " + shiftX + ", " + shiftY + ", " + shiftZ);

            // IMPORTANT: The texture shift direction is opposite to world offset change
            // If we want to shift the texture content to the right, we need negative texture coordinates
            shiftTextureContent(-shiftX, -shiftY, -shiftZ);

            // Update world offsets to move the texture window
            worldOffsetX += shiftX;
            worldOffsetY += shiftY;
            worldOffsetZ += shiftZ;

            System.out.println("New world offsets: " + worldOffsetX + ", " + worldOffsetY + ", " + worldOffsetZ);

            // Update tracking
            lastCameraPos = currentCameraPos;
        }
    }

    /**
     * Allocate (or re‐allocate) the 512×512×384 R16 3D trail texture.
     * Clears its contents to zero so no garbage appears the first frame.
     *
     * SAVES & RESTORES:
     *   • GL_FRAMEBUFFER_BINDING
     *   • GL_TEXTURE_BINDING_3D
     *   • GL_VIEWPORT
     */
    @Unique
    private void allocateOrResizeTrailTexture() {
        int winW = Minecraft.getInstance().getWindow().getWidth();
        int winH = Minecraft.getInstance().getWindow().getHeight();

        // If already allocated and window size hasn't changed, do nothing.
        if (trailTextureId != -1 && lastWindowWidth == winW && lastWindowHeight == winH) {
            return;
        }

        // Delete old textures if they exist
        if (trailTextureId != -1) {
            GL11.glDeleteTextures(trailTextureId);
            trailTextureId = -1;
        }
        if (tempTrailTextureId != -1) {
            GL11.glDeleteTextures(tempTrailTextureId);
            tempTrailTextureId = -1;
        }

        lastWindowWidth  = winW;
        lastWindowHeight = winH;

        // ——————————————— SAVE CURRENT GL STATE ———————————————
        // Save currently bound TEXTURE_3D
        int prevTex3D = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);

        // ——————————————— ALLOCATE & CLEAR NEW 3D TRAIL TEXTURE ———————————————
        // 1) Create a new 512×512×384 R16 3D texture (single channel, 16‐bit)
        trailTextureId = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, trailTextureId);

        // Allocate the 3D texture
        GL12.glTexImage3D(
                GL12.GL_TEXTURE_3D,
                0,
                GL30.GL_R16,          // Single channel, 16‐bit format
                trailTexWidth,
                trailTexHeight,
                trailTexDepth,
                0,
                GL11.GL_RED,         // Single red channel
                GL11.GL_UNSIGNED_SHORT,  // 16-bit data
                (ByteBuffer) null
        );

        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);

        // 2) Clear the 3D texture to zero using glClearTexImage (OpenGL 4.4+)
        // If not available, we'll rely on the initial zero state from null buffer
        try {
            // This requires OpenGL 4.4+, but provides a clean way to clear 3D textures
            GL44.glClearTexImage(trailTextureId, 0, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, (ByteBuffer) null);
        } catch (Exception e) {
            // Fallback: texture should be initialized to zero from null buffer allocation
            System.out.println("Warning: Could not clear 3D texture, relying on zero initialization");
        }

        // ——————————————— RESTORE PREVIOUS GL STATE ———————————————
        // Restore the previous 3D texture binding
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, prevTex3D);

        // 3) Initialize world offsets so camera is centered in this 512×512×384 region
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int camBlockX = (int) Math.floor(camPos.x + 64); // Add 64 to avoid negatives
        int camBlockY = (int) Math.floor(camPos.y + 64); // Add 64 to avoid negatives
        int camBlockZ = (int) Math.floor(camPos.z + 64); // Add 64 to avoid negatives

        worldOffsetX = camBlockX - (trailTexWidth  / 2);  // 256 = 512/2
        worldOffsetY = camBlockY - (trailTexDepth  / 2);  // 192 = 384/2
        worldOffsetZ = camBlockZ - (trailTexHeight / 2);  // 256 = 512/2

        // Reset texture initialization flag
        textureInitialized = false;

        // Initialize compute shader
        initializeComputeShader();
    }

    // Add these fields to your class
    @Unique private final Map<RenderType, Long> lastCallTimes = new HashMap<>();
    @Unique private final Map<RenderType, Float> cachedDeltaTimes = new HashMap<>();

    /**
     * Get delta time in seconds since the last call for this specific RenderType.
     * First call for a RenderType returns 0.0f.
     *
     * @param renderType The render type to track timing for
     * @return Delta time in seconds since last call, or 0.0f for first call
     */
    @Unique
    private float getDeltaTimeFor(RenderType renderType) {
        long currentTime = System.nanoTime();
        Long lastTime = lastCallTimes.get(renderType);

        if (lastTime == null) {
            // First call for this RenderType
            lastCallTimes.put(renderType, currentTime);
            cachedDeltaTimes.put(renderType, 0.0f);
            return 0.0f;
        }

        // Calculate delta in seconds
        float deltaSeconds = (currentTime - lastTime) / 1_000_000_000.0f;

        // Update tracking
        lastCallTimes.put(renderType, currentTime);
        cachedDeltaTimes.put(renderType, deltaSeconds);

        return deltaSeconds;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true)
    private void renderSectionLayer(
            RenderType renderType,
            double x, double y, double z,
            Matrix4f frustrumMatrix,
            Matrix4f projectionMatrix,
            CallbackInfo ci
    ) {
        if (Nanosuit.currentClientMode != SuitModes.VISOR.get()) return;
        RenderSystem.assertOnRenderThread();
        renderType.setupRenderState();

        // 1) Ensure our 512×512×384 3D trail texture exists (allocate on first use)
        if (trailTextureId == -1) {
            allocateOrResizeTrailTexture();
        }

        // 1.5) Check if we need to shift the texture based on camera movement
        Vec3 currentCameraPos = this.minecraft.gameRenderer.getMainCamera().getPosition();
        updateTexturePosition(currentCameraPos);

        // 2) Vanilla translucent‐sorting logic (unchanged)
        if (renderType == RenderType.translucent()) {
            this.minecraft.getProfiler().push("translucent_sort");
            double d0 = x - this.xTransparentOld;
            double d1 = y - this.yTransparentOld;
            double d2 = z - this.zTransparentOld;
            if (d0 * d0 + d1 * d1 + d2 * d2 > 1.0D) {
                int i = SectionPos.posToSectionCoord(x);
                int j = SectionPos.posToSectionCoord(y);
                int k = SectionPos.posToSectionCoord(z);
                boolean flag = i != SectionPos.posToSectionCoord(this.xTransparentOld)
                        || k != SectionPos.posToSectionCoord(this.zTransparentOld)
                        || j != SectionPos.posToSectionCoord(this.yTransparentOld);
                this.xTransparentOld = x;
                this.yTransparentOld = y;
                this.zTransparentOld = z;
                int l = 0;
                ObjectListIterator<SectionRenderDispatcher.RenderSection> var21 =
                        this.visibleSections.iterator();
                while (var21.hasNext()) {
                    SectionRenderDispatcher.RenderSection section = var21.next();
                    if (l < 15
                            && (flag || section.isAxisAlignedWith(i, j, k))
                            && section.resortTransparency(renderType, this.sectionRenderDispatcher)) {
                        ++l;
                    }
                }
            }
            this.minecraft.getProfiler().pop();
        }
        this.minecraft.getProfiler().push("filterempty");
        this.minecraft.getProfiler().popPush(() -> "render_" + renderType);

        boolean solidPass = (renderType != RenderType.translucent());
        ObjectListIterator<SectionRenderDispatcher.RenderSection> it =
                this.visibleSections.listIterator(solidPass ? 0 : this.visibleSections.size());

        // 3) Select the appropriate infrared shader
        ShaderInstance shaderInstance;
        // (we've hard‐coded SOLID_SHADER for now)
        shaderInstance = InfraredShader.Blocks.SOLID_SHADER;

        // 4) Gather up to 16 nearby "hot" entities
        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        double searchRadius = 20.0;
        AABB box = new AABB(
                camPos.x - searchRadius, camPos.y - searchRadius, camPos.z - searchRadius,
                camPos.x + searchRadius, camPos.y + searchRadius, camPos.z + searchRadius
        );
        List<Entity> nearby = this.minecraft.level.getEntities(null, box);

        float[] entityData = new float[128]; // 16 × (2 × vec4)
        int count = 0;
        for (Entity e : nearby) {
            if (count >= 16) break;
            Vec3 epWorld = e.position();
            float ex = (float) (epWorld.x - camPos.x);
            float ey = (float) (epWorld.y - camPos.y);
            float ez = (float) (epWorld.z - camPos.z);

            float width  = e.getBbWidth()  * 4 * e.getBbHeight();
            float height = e.getBbHeight() * 4 * e.getBbHeight();
            ey += e.getBbHeight() / 2.0F;

            int base = count * 8;
            entityData[base + 0] = ex;
            entityData[base + 1] = ey;
            entityData[base + 2] = ez;
            entityData[base + 3] = width;
            entityData[base + 4] = height;
            entityData[base + 5] = 0f; // unused
            entityData[base + 6] = 0f; // unused
            entityData[base + 7] = 0f; // unused
            count++;
        }

        // 5) Apply the shader and upload uniforms
        shaderInstance.setDefaultUniforms(
                VertexFormat.Mode.QUADS,
                frustrumMatrix,
                projectionMatrix,
                this.minecraft.getWindow()
        );
        shaderInstance.apply();

        // Upload EntityCount
        var ec = shaderInstance.getUniform("EntityCount");
        if (ec != null) {
            ec.set(count);
            ec.upload();
        }

        // Upload EntityData[]
        var ed = shaderInstance.getUniform("EntityData");
        if (ed != null) {
            ed.set(entityData);
            ed.upload();
        }

        // Upload CameraPos
        var cwp = shaderInstance.getUniform("CameraPos");
        if (cwp != null) {
            Vec3 c = this.minecraft.gameRenderer.getMainCamera().getPosition();
            cwp.set((float) c.x, (float) c.y, (float) c.z);
            cwp.upload();
        }

        // 6) Upload block world offsets (no longer multiplied by 16)
        var uOffX = shaderInstance.getUniform("u_worldOffsetX");
        if (uOffX != null) {
            uOffX.set(worldOffsetX);
            uOffX.upload();
        }
        var uOffY = shaderInstance.getUniform("u_worldOffsetY");
        if (uOffY != null) {
            uOffY.set(worldOffsetY);
            uOffY.upload();
        }
        var uOffZ = shaderInstance.getUniform("u_worldOffsetZ");
        if (uOffZ != null) {
            uOffZ.set(worldOffsetZ);
            uOffZ.upload();
        }

        var dt = shaderInstance.getUniform("u_deltaTime");
        if (dt != null) {
            dt.set(getDeltaTimeFor(renderType));
            dt.upload();
        }

        // ——————————————— SAVE CURRENT ACTIVE TEXTURE & BINDINGS ———————————————
        int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        // Save what is bound at unit 0
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        // Save what is bound at unit 2 (3D texture)
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        int prevTex2 = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);

        // ——————————————— BIND OUR 3D TRAIL TEXTURE FOR READ/WRITE ———————————————
        // Bind as image unit 1 (write) & sampler unit 2 (read)
        GL42.glBindImageTexture(
                /* unit     */ 1,
                /* texture  */ trailTextureId,
                /* level    */ 0,
                /* layered  */ true,  // This is a 3D texture, so layered = true
                /* layer    */ 0,     // Not used for 3D textures when layered = true
                /* access   */ GL15.GL_READ_WRITE,
                /* format   */ GL30.GL_R16
        );

        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, trailTextureId);
        var trailSampler = shaderInstance.getUniform("TrailSampler");
        if (trailSampler != null) {
            trailSampler.set(2);
            trailSampler.upload();
        }

        // 8) Draw each chunk section exactly as vanilla, but with our custom shader
        var chunkOffset = shaderInstance.getUniform("ChunkOffset");
        while (solidPass ? it.hasNext() : it.hasPrevious()) {
            SectionRenderDispatcher.RenderSection section =
                    solidPass ? it.next() : it.previous();
            if (!section.getCompiled().isEmpty(renderType)) {
                VertexBuffer vb = section.getBuffer(renderType);
                var origin = section.getOrigin();
                if (chunkOffset != null) {
                    float ox = (float) (origin.getX() - x);
                    float oy = (float) (origin.getY() - y);
                    float oz = (float) (origin.getZ() - z);
                    chunkOffset.set(ox, oy, oz);
                    chunkOffset.upload();
                }
                vb.bind();
                vb.draw();
            }
        }

        // Reset chunkOffset to zero
        if (chunkOffset != null) {
            chunkOffset.set(0f, 0f, 0f);
            chunkOffset.upload();
        }

        // 9) CLEAN UP: restore all altered GL state
        //  9a) Un‐bind our image unit (unit 1 → no texture)
        GL42.glBindImageTexture(
                /* unit     */ 1,
                /* texture  */ 0,
                /* level    */ 0,
                /* layered  */ false,
                /* layer    */ 0,
                /* access   */ GL15.GL_READ_WRITE,
                /* format   */ GL30.GL_R16
        );

        //  9b) Restore texture‐unit 2's binding (3D texture)
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, prevTex2);

        //  9c) Restore texture‐unit 0's binding
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex0);

        //  9d) Restore whichever active texture was in use
        GL13.glActiveTexture(prevActiveTex);

        // 10) Clean up exactly as vanilla
        shaderInstance.clear();
        VertexBuffer.unbind();
        this.minecraft.getProfiler().pop();
        ClientHooks.dispatchRenderStage(
                renderType,
                (LevelRenderer) (Object) this,
                frustrumMatrix,
                projectionMatrix,
                this.ticks,
                this.minecraft.gameRenderer.getMainCamera(),
                this.getFrustum()
        );
        renderType.clearRenderState();
        ci.cancel();
    }
}