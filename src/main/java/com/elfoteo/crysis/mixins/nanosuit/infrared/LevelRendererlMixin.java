package com.elfoteo.crysis.mixins.nanosuit.infrared;

import com.elfoteo.crysis.gui.util.EntityDisposition;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SetSectionRenderDispatcher;
import com.elfoteo.crysis.util.SuitModes;
import com.elfoteo.crysis.util.TrailTextureManager;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

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

        // Ensure our 3D trail texture exists
        TrailTextureManager.allocateOrResizeIfNeeded();

        // Possibly shift texture (if camera moved far enough)
        Vec3 currentCameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        TrailTextureManager.updateTexturePosition(currentCameraPos);

        // Vanilla translucent sorting (unchanged)
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

                for (SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection : this.visibleSections) {
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

        // Select your infrared shader (hard‐coded to SOLID_SHADER here)
        ShaderInstance shaderInstance = InfraredShader.Blocks.SOLID_SHADER;
        shaderInstance.setDefaultUniforms(
                VertexFormat.Mode.QUADS,
                frustrumMatrix,
                projectionMatrix,
                minecraft.getWindow()
        );
        shaderInstance.apply();

        // Bind the 3D trail texture and sampler (now inside TrailTextureManager)
        TrailTextureManager.bindForRender(shaderInstance, renderType, true);

        // Draw all visible sections with our custom shader
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

        // Restore GL state (unbind image unit, restore texture bindings)
        //     (now inside TrailTextureManager)
        TrailTextureManager.unbindAfterRender();

        // Cleanup: clear shader, unbind VB, pop profiler, cancel
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