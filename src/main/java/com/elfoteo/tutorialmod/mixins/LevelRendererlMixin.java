package com.elfoteo.tutorialmod.mixins;

import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.SetSectionRenderDispatcher;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    /*@Inject(method = "renderSectionLayer", at=@At("HEAD"))
    private void renderSectionLayerStart(
            RenderType renderType, double x, double y, double z, Matrix4f frustrumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        RenderSystem.setShaderColor(0f, 0f, 255f, 1.0F); // out of scale on purpose
    }

    @Inject(method = "renderSectionLayer", at=@At("TAIL"))
    private void renderSectionLayerEnd(
            RenderType renderType, double x, double y, double z, Matrix4f frustrumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1.0F);
    }*/

//    @Inject(method = "renderSectionLayer", at=@At("HEAD"), cancellable = true)
//    private void renderSectionLayer(RenderType renderType, double x, double y, double z, Matrix4f frustrumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
//        RenderSystem.assertOnRenderThread();
//        renderType.setupRenderState();
//
//        if (renderType == RenderType.translucent()) {
//            this.minecraft.getProfiler().push("translucent_sort");
//            double d0 = x - this.xTransparentOld;
//            double d1 = y - this.yTransparentOld;
//            double d2 = z - this.zTransparentOld;
//            if (d0 * d0 + d1 * d1 + d2 * d2 > (double)1.0F) {
//                int i = SectionPos.posToSectionCoord(x);
//                int j = SectionPos.posToSectionCoord(y);
//                int k = SectionPos.posToSectionCoord(z);
//                boolean flag = i != SectionPos.posToSectionCoord(this.xTransparentOld) || k != SectionPos.posToSectionCoord(this.zTransparentOld) || j != SectionPos.posToSectionCoord(this.yTransparentOld);
//                this.xTransparentOld = x;
//                this.yTransparentOld = y;
//                this.zTransparentOld = z;
//                int l = 0;
//                ObjectListIterator var21 = this.visibleSections.iterator();
//
//                while(var21.hasNext()) {
//                    SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection = (SectionRenderDispatcher.RenderSection)var21.next();
//                    if (l < 15 && (flag || sectionrenderdispatcher$rendersection.isAxisAlignedWith(i, j, k)) && sectionrenderdispatcher$rendersection.resortTransparency(renderType, this.sectionRenderDispatcher)) {
//                        ++l;
//                    }
//                }
//            }
//
//            this.minecraft.getProfiler().pop();
//        }
//
//        this.minecraft.getProfiler().push("filterempty");
//        this.minecraft.getProfiler().popPush(() -> "render_" + String.valueOf(renderType));
//        boolean flag1 = renderType != RenderType.translucent();
//        ObjectListIterator<SectionRenderDispatcher.RenderSection> objectlistiterator = this.visibleSections.listIterator(flag1 ? 0 : this.visibleSections.size());
//        ShaderInstance shaderinstance = RenderSystem.getShader();
//        shaderinstance.setDefaultUniforms(VertexFormat.Mode.QUADS, frustrumMatrix, projectionMatrix, this.minecraft.getWindow());
//        shaderinstance.apply();
//        Uniform uniform = shaderinstance.CHUNK_OFFSET;
//
//        while(true) {
//            if (flag1) {
//                if (!objectlistiterator.hasNext()) {
//                    break;
//                }
//            } else if (!objectlistiterator.hasPrevious()) {
//                break;
//            }
//
//            SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection1 = flag1 ? (SectionRenderDispatcher.RenderSection)objectlistiterator.next() : (SectionRenderDispatcher.RenderSection)objectlistiterator.previous();
//            if (!sectionrenderdispatcher$rendersection1.getCompiled().isEmpty(renderType)) {
//                VertexBuffer vertexbuffer = sectionrenderdispatcher$rendersection1.getBuffer(renderType);
//                BlockPos blockpos = sectionrenderdispatcher$rendersection1.getOrigin();
//
//                int hash = blockpos.hashCode();
//                float r = ((hash >> 16) & 0xFF) / 255.0f;
//                float g = ((hash >> 8) & 0xFF) / 255.0f;
//                float b = (hash & 0xFF) / 255.0f;
//
//                RenderSystem.setShaderColor(r, g, b, 1.0f); // Set section color
//                if (uniform != null) {
//                    uniform.set((float)((double)blockpos.getX() - x), (float)((double)blockpos.getY() - y), (float)((double)blockpos.getZ() - z));
//                    uniform.upload();
//                }
//
//                if (uniform != null) {
//                    uniform.set((float)((double)blockpos.getX() - x), (float)((double)blockpos.getY() - y), (float)((double)blockpos.getZ() - z));
//                    uniform.upload();
//                }
//
//                vertexbuffer.bind();
//                vertexbuffer.draw();
//            }
//        }
//
//        if (uniform != null) {
//            uniform.set(0.0F, 0.0F, 0.0F);
//        }
//
//        shaderinstance.clear();
//        VertexBuffer.unbind();
//        this.minecraft.getProfiler().pop();
//        ClientHooks.dispatchRenderStage(renderType, (LevelRenderer) (Object) this, frustrumMatrix, projectionMatrix, this.ticks, this.minecraft.gameRenderer.getMainCamera(), this.getFrustum());
//        renderType.clearRenderState();
//
//        RenderSystem.setShaderColor(1, 1, 1, 1.0f); // Set section color
//        ci.cancel();
//    }
}
