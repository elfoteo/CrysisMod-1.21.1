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
    @Unique private int trailTextureId = -1;
    @Unique private int trailFramebufferId = -1;
    @Unique private int lastWindowWidth = -1;
    @Unique private int lastWindowHeight = -1;

    // Define 3D texture dimensions (in blocks)
    @Unique private final int trailTexSizeX = 512; // x dimension
    @Unique private final int trailTexSizeY = 380;  // y dimension (height)
    @Unique private final int trailTexSizeZ = 512; // z dimension

    @Unique private int worldOffsetX = 0; // In blocks
    @Unique private int worldOffsetZ = 0; // In blocks
    @Unique private int lastPlayerBlockX = Integer.MAX_VALUE; // Track player position
    @Unique private int lastPlayerBlockZ = Integer.MAX_VALUE;

    // Temporary texture for data shifting
    @Unique private int tempTextureId = -1;
    @Unique private int shiftThreshold = 32; // Shift when player moves this many blocks

    // Compute shader for shifting texture data
    @Unique private int shiftComputeProgram = -1;

    private void initShiftComputeShader() {
        if (shiftComputeProgram != -1) return;

        String computeShaderSource = """
        #version 430
        layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;
        
        layout(binding = 0, r16f) uniform image3D sourceTexture;
        layout(binding = 1, r16f) uniform image3D destTexture;
        
        uniform ivec3 textureSize;
        uniform ivec3 offset;
        
        void main() {
            ivec3 destCoord = ivec3(gl_GlobalInvocationID);
            
            // Check bounds
            if (destCoord.x >= textureSize.x || 
                destCoord.y >= textureSize.y || 
                destCoord.z >= textureSize.z) {
                return;
            }
            
            // Calculate source coordinate with offset
            ivec3 sourceCoord = destCoord - offset;
            
            float value = 0.0;
            
            // Check if source coordinate is within bounds
            if (sourceCoord.x >= 0 && sourceCoord.x < textureSize.x &&
                sourceCoord.y >= 0 && sourceCoord.y < textureSize.y &&
                sourceCoord.z >= 0 && sourceCoord.z < textureSize.z) {
                value = imageLoad(sourceTexture, sourceCoord).r;
            }
            
            imageStore(destTexture, destCoord, vec4(value, 0.0, 0.0, 0.0));
        }
        """;

        // Compile compute shader
        int computeShader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(computeShader, computeShaderSource);
        GL20.glCompileShader(computeShader);

        if (GL20.glGetShaderi(computeShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(computeShader);
            GL20.glDeleteShader(computeShader);
            throw new RuntimeException("Failed to compile shift compute shader: " + log);
        }

        // Create program
        shiftComputeProgram = GL20.glCreateProgram();
        GL20.glAttachShader(shiftComputeProgram, computeShader);
        GL20.glLinkProgram(shiftComputeProgram);

        if (GL20.glGetProgrami(shiftComputeProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(shiftComputeProgram);
            GL20.glDeleteProgram(shiftComputeProgram);
            GL20.glDeleteShader(computeShader);
            throw new RuntimeException("Failed to link shift compute shader: " + log);
        }

        GL20.glDeleteShader(computeShader);
    }

    private void allocateOrResizeTrailTexture() {
        int winW = Minecraft.getInstance().getWindow().getWidth();
        int winH = Minecraft.getInstance().getWindow().getHeight();

        if (trailTextureId != -1 && lastWindowWidth == winW && lastWindowHeight == winH) {
            return;
        }

        // Clean up existing resources
        if (trailFramebufferId != -1) {
            GL30.glDeleteFramebuffers(trailFramebufferId);
            trailFramebufferId = -1;
        }
        if (trailTextureId != -1) {
            GL11.glDeleteTextures(trailTextureId);
            trailTextureId = -1;
        }
        if (tempTextureId != -1) {
            GL11.glDeleteTextures(tempTextureId);
            tempTextureId = -1;
        }

        lastWindowWidth = winW;
        lastWindowHeight = winH;

        // Save current GL state
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevTex3D = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

        // Allocate main 3D texture
        trailTextureId = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, trailTextureId);
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R16F, trailTexSizeX, trailTexSizeY, trailTexSizeZ, 0, GL11.GL_RED, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);

        // Allocate temporary texture for shifting
        tempTextureId = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, tempTextureId);
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R16F, trailTexSizeX, trailTexSizeY, trailTexSizeZ, 0, GL11.GL_RED, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);

        // Clear both textures using a framebuffer
        trailFramebufferId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, trailFramebufferId);

        // Clear main texture
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, trailTextureId);
        for (int layer = 0; layer < trailTexSizeY; layer++) {
            GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, trailTextureId, 0, layer);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Framebuffer incomplete for layer " + layer);
            }
            GL11.glViewport(0, 0, trailTexSizeX, trailTexSizeZ);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }

        // Clear temp texture
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, tempTextureId);
        for (int layer = 0; layer < trailTexSizeY; layer++) {
            GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, tempTextureId, 0, layer);
            GL11.glViewport(0, 0, trailTexSizeX, trailTexSizeZ);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }

        // Restore GL state
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, prevTex3D);
        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);

        // Initialize compute shader
        initShiftComputeShader();

        // Reset player tracking
        lastPlayerBlockX = Integer.MAX_VALUE;
        lastPlayerBlockZ = Integer.MAX_VALUE;
    }

    private void updateTextureCenter() {
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int currentPlayerBlockX = (int) Math.floor(camPos.x);
        int currentPlayerBlockZ = (int) Math.floor(camPos.z);

        // Check if this is the first time
        if (lastPlayerBlockX == Integer.MAX_VALUE || lastPlayerBlockZ == Integer.MAX_VALUE) {
            // First time - just set the offsets
            worldOffsetX = currentPlayerBlockX - (trailTexSizeX / 2);
            worldOffsetZ = currentPlayerBlockZ - (trailTexSizeZ / 2);
            lastPlayerBlockX = currentPlayerBlockX;
            lastPlayerBlockZ = currentPlayerBlockZ;
            return;
        }

        int deltaX = currentPlayerBlockX - lastPlayerBlockX;
        int deltaZ = currentPlayerBlockZ - lastPlayerBlockZ;

        // Only shift if player moved beyond threshold
        if (Math.abs(deltaX) >= shiftThreshold || Math.abs(deltaZ) >= shiftThreshold) {
            // Calculate new world offsets FIRST
            int newWorldOffsetX = currentPlayerBlockX - (trailTexSizeX / 2);
            int newWorldOffsetZ = currentPlayerBlockZ - (trailTexSizeZ / 2);

            // Calculate the shift needed in texture space
            // This is the difference between old and new world offsets
            int textureShiftX = newWorldOffsetX - worldOffsetX;
            int textureShiftZ = newWorldOffsetZ - worldOffsetZ;

            // Perform the shift with correct direction
            shiftTextureData(textureShiftX, textureShiftZ);

            // Update offsets and player position AFTER shifting
            worldOffsetX = newWorldOffsetX;
            worldOffsetZ = newWorldOffsetZ;
            lastPlayerBlockX = currentPlayerBlockX;
            lastPlayerBlockZ = currentPlayerBlockZ;
        }
    }

    private void shiftTextureData(int shiftX, int shiftZ) {
        if (trailTextureId == -1 || tempTextureId == -1 || shiftComputeProgram == -1) return;

        // Save current GL state
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        // Use compute shader to shift data
        GL20.glUseProgram(shiftComputeProgram);

        // Bind textures to image units
        GL42.glBindImageTexture(0, trailTextureId, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R16F);
        GL42.glBindImageTexture(1, tempTextureId, 0, true, 0, GL15.GL_WRITE_ONLY, GL30.GL_R16F);

        // Set uniforms
        int textureSizeLoc = GL20.glGetUniformLocation(shiftComputeProgram, "textureSize");
        int offsetLoc = GL20.glGetUniformLocation(shiftComputeProgram, "offset");

        if (textureSizeLoc != -1) {
            GL20.glUniform3i(textureSizeLoc, trailTexSizeX, trailTexSizeY, trailTexSizeZ);
        }
        if (offsetLoc != -1) {
            // The offset represents how much to shift the data
            // Positive offset means data moves in positive direction
            GL20.glUniform3i(offsetLoc, -shiftX, 0, -shiftZ); // Note the negation
        }

        // Dispatch compute shader
        int workGroupsX = (trailTexSizeX + 7) / 8;
        int workGroupsY = (trailTexSizeY + 7) / 8;
        int workGroupsZ = (trailTexSizeZ + 0) / 1;

        GL43.glDispatchCompute(workGroupsX, workGroupsY, workGroupsZ);

        // Wait for completion
        GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

        // Swap textures (temp becomes main)
        int temp = trailTextureId;
        trailTextureId = tempTextureId;
        tempTextureId = temp;

        // Clear the old texture (now temp) for next time
        clearTexture(tempTextureId);

        // Restore GL state
        GL20.glUseProgram(prevProgram);
    }

    // Helper method to clear texture more efficiently
    private void clearTexture(int textureId) {
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, trailFramebufferId);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, textureId);

        for (int layer = 0; layer < trailTexSizeY; layer++) {
            GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, textureId, 0, layer);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE) {
                GL11.glViewport(0, 0, trailTexSizeX, trailTexSizeZ);
                GL11.glClearColor(0f, 0f, 0f, 0f);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            }
        }

        // Restore state
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
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

        // 1) Ensure our trail texture exists and is properly centered
        if (trailTextureId == -1) {
            allocateOrResizeTrailTexture();
        }

        // 2) Update texture center based on player position
        updateTextureCenter();

        // 3) Vanilla translucent‐sorting logic (unchanged)
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

        // 4) Select the appropriate infrared shader
        ShaderInstance shaderInstance;
        shaderInstance = InfraredShader.Blocks.SOLID_SHADER;

        // 5) Gather up to 16 nearby "hot" entities
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

        // 6) Apply the shader and upload uniforms
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

        // 7) Upload world offsets (always current and centered)
        var uOffX = shaderInstance.getUniform("u_worldOffsetX");
        if (uOffX != null) {
            uOffX.set(worldOffsetX);
            uOffX.upload();
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

        // 8) Save current active texture & bindings
        int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex3D_0 = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        int prevTex3D_2 = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, trailTextureId);
        GL42.glBindImageTexture(1, trailTextureId, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R16F);

        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, trailTextureId);
        var trailSampler = shaderInstance.getUniform("TrailSampler");
        if (trailSampler != null) {
            trailSampler.set(2);
            trailSampler.upload();
        }

        // 9) Draw each chunk section
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

        // Restore GL state
        GL42.glBindImageTexture(1, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R16F);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, prevTex3D_2);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, prevTex3D_0);
        GL13.glActiveTexture(prevActiveTex);

        // 10) Clean up
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
