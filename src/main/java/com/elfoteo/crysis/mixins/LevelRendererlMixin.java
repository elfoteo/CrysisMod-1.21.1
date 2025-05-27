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
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.List;

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

    @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true)
    private void renderSectionLayer(RenderType renderType,
                                    double x,
                                    double y,
                                    double z,
                                    Matrix4f frustrumMatrix,
                                    Matrix4f projectionMatrix,
                                    CallbackInfo ci) {
        RenderSystem.assertOnRenderThread();
        renderType.setupRenderState();

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

        this.minecraft.getProfiler().push("filterempty");
        this.minecraft.getProfiler().popPush(() -> "render_" + renderType);
        boolean solidPass = renderType != RenderType.translucent();
        ObjectListIterator<SectionRenderDispatcher.RenderSection> it =
                this.visibleSections.listIterator(solidPass ? 0 : this.visibleSections.size());

        // ─── A) Choose the correct ShaderInstance for this RenderType:
        ShaderInstance shaderinstance;
        if (renderType == RenderType.solid()) {
            shaderinstance = InfraredShader.Blocks.SOLID_SHADER;
        } else if (renderType == RenderType.cutout()) {
            shaderinstance = InfraredShader.Blocks.CUTOUT_SHADER;
        } else if (renderType == RenderType.cutoutMipped()) {
            shaderinstance = InfraredShader.Blocks.CUTOUT_MIPPED_SHADER;
        } else if (renderType == RenderType.translucent()) {
            shaderinstance = InfraredShader.Blocks.TRANSLUCENT_SHADER;
        } else if (renderType == RenderType.tripwire()) {
            shaderinstance = InfraredShader.Blocks.TRIPWIRE_SHADER;
        } else {
            shaderinstance = InfraredShader.Blocks.SOLID_SHADER;
        }

        // ─── B) Build up to 16 “hot” entities in camera‐relative coords:
        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        double searchRadius = 20.0;
        AABB box = new AABB(
                camPos.x - searchRadius, camPos.y - searchRadius, camPos.z - searchRadius,
                camPos.x + searchRadius, camPos.y + searchRadius, camPos.z + searchRadius
        );
        List<Entity> nearby = this.minecraft.level.getEntities(null, box);

        float[] entityData = new float[64];  // 16 × (x,y,z,radius)
        int    count = 0;
        for (Entity e : nearby) {
            if (count >= 16) break;

            // Absolute entity world position:
            Vec3 epWorld = e.position();
            // Convert to camera-relative:
            float ex = (float)(epWorld.x - camPos.x);
            float ey = (float)(epWorld.y - camPos.y);
            float ez = (float)(epWorld.z - camPos.z);

            // Choose a radius for the “hot spot”:
            float radius = 1.0f;
            if (e instanceof LivingEntity) {
                radius = 3.0f;
            } else if (e instanceof ItemEntity) {
                radius = 1.0f;
            }

            int base = count * 4;
            entityData[base + 0] = ex;
            entityData[base + 1] = ey;
            entityData[base + 2] = ez;
            entityData[base + 3] = radius;
            count++;
        }

        // ─── D) Upload the usual uniforms (matrices, chunk offset, etc.)
        shaderinstance.setDefaultUniforms(
                VertexFormat.Mode.QUADS,
                frustrumMatrix,
                projectionMatrix,
                this.minecraft.getWindow()
        );
        shaderinstance.apply();

        // ─── C) Upload EntityData[] and EntityCount to the shader:
        Uniform ec = shaderinstance.getUniform("EntityCount");
        if (ec != null){
            ec.set(count);
            ec.upload();
        }

        Uniform ed = shaderinstance.getUniform("EntityData");
        if (ed != null) {
            ed.set(entityData);
            ed.upload();
        }

        Uniform cwp = shaderinstance.getUniform("CameraPos");
        if (cwp != null){
            Vec3 cam = this.minecraft.gameRenderer.getMainCamera().getPosition();
            cwp.set((float)cam.x, (float)cam.y, (float)cam.z);
            cwp.upload();
        }

        // ─── E) Draw each section exactly as before, but now with our updated shader
        Uniform chunkOffset = shaderinstance.CHUNK_OFFSET;
        while (solidPass ? it.hasNext() : it.hasPrevious()) {
            SectionRenderDispatcher.RenderSection section =
                    solidPass ? it.next() : it.previous();
            if (!section.getCompiled().isEmpty(renderType)) {
                VertexBuffer vb = section.getBuffer(renderType);
                BlockPos origin = section.getOrigin();
                if (chunkOffset != null) {
                    float ox = (float)(origin.getX() - x);
                    float oy = (float)(origin.getY() - y);
                    float oz = (float)(origin.getZ() - z);
                    chunkOffset.set(ox, oy, oz);
                    chunkOffset.upload();
                }
                vb.bind();
                vb.draw();
            }
        }

        if (chunkOffset != null) {
            chunkOffset.set(0f, 0f, 0f);
            chunkOffset.upload();
        }
        shaderinstance.clear();
        VertexBuffer.unbind();
        this.minecraft.getProfiler().pop();
        net.neoforged.neoforge.client.ClientHooks.dispatchRenderStage(
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
