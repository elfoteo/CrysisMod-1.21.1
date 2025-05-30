package com.elfoteo.crysis.mixins;

import com.elfoteo.crysis.gui.util.EntityDisposition;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SetSectionRenderDispatcher;
import com.elfoteo.crysis.util.SuitModes;
import com.elfoteo.crysis.util.TrailTextureManager;
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

    // Keep a single manager to handle the 3D texture & shifting logic:
    @Unique
    private final TrailTextureManager trailManager = new TrailTextureManager();

    @Inject(
            method = "renderSectionLayer",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRenderSectionLayer(
            RenderType renderType,
            double x, double y, double z,
            Matrix4f frustrumMatrix,
            Matrix4f projectionMatrix,
            CallbackInfo ci
    ) {
        // Only run our code if we’re in “VISOR” mode (replace SuitModes.VISOR with whatever you use)
        if (Nanosuit.currentClientMode != SuitModes.VISOR.get()) {
            return;
        }

        RenderSystem.assertOnRenderThread();
        renderType.setupRenderState();

        // ———————————————————————————————
        // 1) Ensure our 3D trail texture exists
        // ———————————————————————————————
        trailManager.allocateOrResizeIfNeeded();

        // ———————————————————————————————
        // 2) Possibly shift texture (if camera moved far enough)
        // ———————————————————————————————
        Vec3 currentCameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        trailManager.updateTexturePosition(currentCameraPos);

        // ———————————————————————————————
        // 3) Vanilla translucent sorting (unchanged)
        // ———————————————————————————————
        if (renderType == RenderType.translucent()) {
            this.minecraft.getProfiler().push("translucent_sort");
            double d0 = x - this.xTransparentOld;
            double d1 = y - this.yTransparentOld;
            double d2 = z - this.zTransparentOld;
            if (d0 * d0 + d1 * d1 + d2 * d2 > (double)1.0F) {
                int i = SectionPos.posToSectionCoord(x);
                int j = SectionPos.posToSectionCoord(y);
                int k = SectionPos.posToSectionCoord(z);
                boolean flag = i != SectionPos.posToSectionCoord(this.xTransparentOld) || k != SectionPos.posToSectionCoord(this.zTransparentOld) || j != SectionPos.posToSectionCoord(this.yTransparentOld);
                this.xTransparentOld = x;
                this.yTransparentOld = y;
                this.zTransparentOld = z;
                int l = 0;
                ObjectListIterator var21 = this.visibleSections.iterator();

                while(var21.hasNext()) {
                    SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection = (SectionRenderDispatcher.RenderSection)var21.next();
                    if (l < 15 && (flag || sectionrenderdispatcher$rendersection.isAxisAlignedWith(i, j, k)) && sectionrenderdispatcher$rendersection.resortTransparency(renderType, this.sectionRenderDispatcher)) {
                        ++l;
                    }
                }
            }

            this.minecraft.getProfiler().pop();
        }
        minecraft.getProfiler().push("filterempty");
        minecraft.getProfiler().popPush(() -> "render_" + renderType);

        boolean solidPass = (renderType != RenderType.translucent());
        ObjectListIterator<SectionRenderDispatcher.RenderSection> objectlistiterator = this.visibleSections.listIterator(solidPass ? 0 : this.visibleSections.size());

        // ———————————————————————————————
        // 4) Select your infrared shader (hard‐coded to SOLID_SHADER here)
        // ———————————————————————————————
        ShaderInstance shaderInstance = InfraredShader.Blocks.SOLID_SHADER;
        shaderInstance.setDefaultUniforms(
                VertexFormat.Mode.QUADS,
                frustrumMatrix,
                projectionMatrix,
                minecraft.getWindow()
        );
        shaderInstance.apply();

        // ———————————————————————————————
        // 5) Upload “hot entities” data (unchanged from your mixin)
        // ———————————————————————————————
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        double searchRadius = 20.0;
        AABB box = new AABB(
                camPos.x - searchRadius, camPos.y - searchRadius, camPos.z - searchRadius,
                camPos.x + searchRadius, camPos.y + searchRadius, camPos.z + searchRadius
        );
        List<Entity> nearby = minecraft.level.getEntities(null, box);

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
            // leftover slots stay zero
            count++;
        }

        var ec = shaderInstance.getUniform("EntityCount");
        if (ec != null) {
            ec.set(count);
            ec.upload();
        }
        var ed = shaderInstance.getUniform("EntityData");
        if (ed != null) {
            ed.set(entityData);
            ed.upload();
        }

        var cwp = shaderInstance.getUniform("CameraPos");
        if (cwp != null) {
            Vec3 c = minecraft.gameRenderer.getMainCamera().getPosition();
            cwp.set((float) c.x, (float) c.y, (float) c.z);
            cwp.upload();
        }

        // ———————————————————————————————
        // 6) Upload world offsets from TrailTextureManager
        // ———————————————————————————————
        var uOffX = shaderInstance.getUniform("u_worldOffsetX");
        if (uOffX != null) {
            uOffX.set(trailManager.getWorldOffsetX());
            uOffX.upload();
        }
        var uOffY = shaderInstance.getUniform("u_worldOffsetY");
        if (uOffY != null) {
            uOffY.set(trailManager.getWorldOffsetY());
            uOffY.upload();
        }
        var uOffZ = shaderInstance.getUniform("u_worldOffsetZ");
        if (uOffZ != null) {
            uOffZ.set(trailManager.getWorldOffsetZ());
            uOffZ.upload();
        }

        // ———————————————————————————————
        // 7) Upload deltaTime uniform
        // ———————————————————————————————
        var dt = shaderInstance.getUniform("u_deltaTime");
        if (dt != null) {
            dt.set(trailManager.getDeltaTimeFor(renderType));
            dt.upload();
        }

        // ———————————————————————————————
        // 8) Bind the 3D trail texture and sampler (now inside TrailTextureManager)
        // ———————————————————————————————
        trailManager.bindForRender(shaderInstance);

        // ———————————————————————————————
        // 9) Draw all visible sections with our custom shader
        // ———————————————————————————————
        var chunkOffset = shaderInstance.getUniform("ChunkOffset");
        while (true) {
            if (solidPass) {
                if (!objectlistiterator.hasNext()) {
                    break;
                }
            } else if (!objectlistiterator.hasPrevious()) {
                break;
            }

            SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection1 = solidPass
                    ? objectlistiterator.next()
                    : objectlistiterator.previous();

            if (!sectionrenderdispatcher$rendersection1.getCompiled().isEmpty(renderType)) {
                VertexBuffer vertexbuffer = sectionrenderdispatcher$rendersection1.getBuffer(renderType);
                BlockPos blockpos = sectionrenderdispatcher$rendersection1.getOrigin();
                if (chunkOffset != null) {
                    chunkOffset.set((float)((double)blockpos.getX() - x),
                            (float)((double)blockpos.getY() - y),
                            (float)((double)blockpos.getZ() - z));
                    chunkOffset.upload();
                }

                vertexbuffer.bind();
                vertexbuffer.draw();
            }
        }

        // Reset chunkOffset if needed
        if (chunkOffset != null) {
            chunkOffset.set(0f, 0f, 0f);
            chunkOffset.upload();
        }

        // ———————————————————————————————
        // 10) Restore GL state (unbind image unit, restore texture bindings)
        //     (now inside TrailTextureManager)
        // ———————————————————————————————
        trailManager.unbindAfterRender();

        // ———————————————————————————————
        // 11) Cleanup: clear shader, unbind VB, pop profiler, cancel
        // ———————————————————————————————
        shaderInstance.clear();
        VertexBuffer.unbind();
        minecraft.getProfiler().pop();
        ClientHooks.dispatchRenderStage(
                renderType,
                (LevelRenderer)(Object)this,
                frustrumMatrix,
                projectionMatrix,
                this.ticks,  // or ticks if you track ticks
                this.minecraft.gameRenderer.getMainCamera(),
                getFrustum()
        );
        renderType.clearRenderState();
        ci.cancel();
    }
}