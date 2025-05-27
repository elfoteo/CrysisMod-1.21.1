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
// Single "world‐space" trail texture at 16× resolution per block:
    @Unique private int trailTextureId     = -1;
    @Unique private int trailFramebufferId = -1;
    @Unique private int lastWindowWidth    = -1;
    @Unique private int lastWindowHeight   = -1;

    // Cover a 1024×1024‐block region at 16×16 sub‐pixels each → 16384×16384 texture
    @Unique private final int trailTexWidth  = 1024 * 16; // 16384
    @Unique private final int trailTexHeight = 1024 * 16; // 16384

    // Which "sub‐block" X/Z corresponds to texture coordinate (0,0)? (set per frame)
    @Unique private int worldOffsetX = 0;
    @Unique private int worldOffsetZ = 0;

    /**
     * Allocate (or re-allocate) the 16384×16384 R8 trail texture.
     * Clears its contents to zero so no garbage appears the first frame.
     */
    @Unique
    private void allocateOrResizeTrailTexture() {
        int winW = Minecraft.getInstance().getWindow().getWidth();
        int winH = Minecraft.getInstance().getWindow().getHeight();

        // If already allocated and window size hasn't changed, do nothing.
        if (trailTextureId != -1 && lastWindowWidth == winW && lastWindowHeight == winH) {
            return;
        }

        // Delete old FBO + texture if they exist
        if (trailFramebufferId != -1) {
            GL30.glDeleteFramebuffers(trailFramebufferId);
            trailFramebufferId = -1;
        }
        if (trailTextureId != -1) {
            GL11.glDeleteTextures(trailTextureId);
            trailTextureId = -1;
        }

        lastWindowWidth  = winW;
        lastWindowHeight = winH;

        // 1) Create a new 16384×16384 r16 texture (single channel, 8-bit)
        trailTextureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, trailTextureId);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL30.GL_R16,          // Single channel, 8-bit format
                trailTexWidth,
                trailTexHeight,
                0,
                GL11.GL_RED,         // Single red channel
                GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null
        );
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        // 2) Attach to a new FBO so we can clear it
        trailFramebufferId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, trailFramebufferId);
        GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                trailTextureId,
                0
        );
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("World-trail framebuffer is incomplete!");
        }

        // 3) Clear it to zero (no heat) so no garbage remains
        GL11.glViewport(0, 0, trailTexWidth, trailTexHeight);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        // 4) Unbind framebuffer + texture
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // 5) Initialize worldOffsetX/Z so camera is centered in this 16384×16384 region
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int camSubX = (int) Math.floor(camPos.x * 16.0); // "sub‐block" X
        int camSubZ = (int) Math.floor(camPos.z * 16.0); // "sub‐block" Z
        worldOffsetX = camSubX - (trailTexWidth  / 2);  // 8192 = 16384/2
        worldOffsetZ = camSubZ - (trailTexHeight / 2);  // 8192 = 16384/2
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
        RenderSystem.assertOnRenderThread();
        renderType.setupRenderState();

        // 1) Ensure our 16384×16384 trail texture exists (allocate on first use)
        if (trailTextureId == -1) {
            allocateOrResizeTrailTexture();
        }

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
        if (renderType == RenderType.solid()) {
            shaderInstance = InfraredShader.Blocks.SOLID_SHADER;
        } else if (renderType == RenderType.cutout()) {
            shaderInstance = InfraredShader.Blocks.CUTOUT_SHADER;
        } else if (renderType == RenderType.cutoutMipped()) {
            shaderInstance = InfraredShader.Blocks.CUTOUT_MIPPED_SHADER;
        } else if (renderType == RenderType.translucent()) {
            shaderInstance = InfraredShader.Blocks.TRANSLUCENT_SHADER;
        } else if (renderType == RenderType.tripwire()) {
            shaderInstance = InfraredShader.Blocks.TRIPWIRE_SHADER;
        } else {
            shaderInstance = InfraredShader.Blocks.SOLID_SHADER;
        }

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

        // 6) Upload "sub‐block" world offsets (already ×16 in Java)
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
            System.out.println(dt);
            dt.set(getDeltaTimeFor(renderType));
            dt.upload();
        }

        // 7) Bind the 16384×16384 trail texture as:
        //    • image unit 1 (for write) → matches layout(binding=1) in GLSL
        //    • sampler unit 2 (for read)  → matches layout(binding=2) in GLSL
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, trailTextureId);
        GL42.glBindImageTexture(
                /* unit     */ 1,
                /* texture  */ trailTextureId,
                /* level    */ 0,
                /* layered  */ false,
                /* layer    */ 0,
                /* access   */ GL15.GL_READ_WRITE,
                /* format   */ GL30.GL_R16    // Changed to R16 format
        );

        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, trailTextureId);
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

        // 9) Clean up exactly as vanilla
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
