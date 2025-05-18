package com.elfoteo.tutorialmod.mixins.particles;

import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.InfraredShader;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Shadow @Final private Map<ParticleRenderType, Queue<Particle>> particles;
    @Shadow @Final private TextureManager textureManager;

    @Unique
    private static final Set<Class<? extends Particle>> HOT_PARTICLES = Set.of(
            FlameParticle.class,
            LavaParticle.class,
            WhiteAshParticle.class,
            AshParticle.class,
            BaseAshSmokeParticle.class,
            DragonBreathParticle.class
    );

    @Unique
    private static final Set<Class<? extends Particle>> WARM_PARTICLES = Set.of(
            SmokeParticle.class,
            LargeSmokeParticle.class,
            CampfireSmokeParticle.class,
            ExplodeParticle.class,
            FireworkParticles.Starter.class,
            HugeExplosionParticle.class,
            HugeExplosionSeedParticle.class,
            CritParticle.class,
            SpellParticle.class,
            NoteParticle.class,
            HeartParticle.class,
            TotemParticle.class,
            SculkChargeParticle.class,
            SculkChargePopParticle.class,
            ShriekParticle.class,
            DustPlumeParticle.class,
            DustParticle.class,
            DustColorTransitionParticle.class,
            DustParticleBase.class,
            TrialSpawnerDetectionParticle.class,
            EndRodParticle.class,
            WhiteSmokeParticle.class,
            SoulParticle.class,
            DragonBreathParticle.class
    );

    @Unique
    private static final Set<Class<? extends Particle>> COLD_PARTICLES = Set.of(
            WaterDropParticle.class,
            SplashParticle.class,
            BubbleParticle.class,
            BubblePopParticle.class,
            BubbleColumnUpParticle.class,
            SnowflakeParticle.class,
            SuspendedParticle.class,
            SuspendedTownParticle.class,
            TerrainParticle.class,
            WaterCurrentDownParticle.class,
            DripParticle.class,
            SquidInkParticle.class
    );

    /**
     * We inject at the very start of ParticleEngine.render(...). If the nanosuit
     * is in VISOR mode, we intercept every ParticleRenderType, map it to our
     * InfraredShader version, and render with that instead.
     */
    @Inject(
            method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void render(
            LightTexture lightTexture,
            Camera camera,
            float partialTick,
            Frustum frustum,
            Predicate<ParticleRenderType> renderTypePredicate,
            CallbackInfo ci
    ) {
        // Only run our infrared override if the suit is in VISOR mode:
        if (Nanosuit.currentClientMode != SuitModes.VISOR.get()) {
            return;
        }

        // Turn on the custom “light layer” and depth test exactly as vanilla does:
        lightTexture.turnOnLightLayer();
        RenderSystem.enableDepthTest();
        RenderSystem.activeTexture(33986);
        RenderSystem.activeTexture(33984);



        // Iterate over each registered vanilla ParticleRenderType key:
        for (ParticleRenderType vanillaType : this.particles.keySet()) {
            if (vanillaType == ParticleRenderType.NO_RENDER || !renderTypePredicate.test(vanillaType)) continue;

            Queue<Particle> queue = this.particles.get(vanillaType);
            if (queue == null || queue.isEmpty()) continue;

            ParticleRenderType infraredType = getParticleRenderType(vanillaType);

            // Buckets based on heat
            List<Particle> hot = new ArrayList<>();
            List<Particle> warm = new ArrayList<>();
            List<Particle> cold = new ArrayList<>();
            List<Particle> neutral = new ArrayList<>();

            for (Particle particle : queue) {
                if (frustum != null && !frustum.isVisible(particle.getRenderBoundingBox(partialTick))) {
                    continue;
                }

                Class<? extends Particle> cls = particle.getClass();
                if (HOT_PARTICLES.contains(cls)) {
                    hot.add(particle);
                } else if (WARM_PARTICLES.contains(cls)) {
                    warm.add(particle);
                } else if (COLD_PARTICLES.contains(cls)) {
                    cold.add(particle);
                } else {
                    neutral.add(particle);
                }
            }

            // Render each bucket with its own heat uniform
            renderBucket(hot, 0.9f, infraredType, camera, partialTick);
            renderBucket(warm, 0.5f, infraredType, camera, partialTick);
            renderBucket(cold, 0.0f, infraredType, camera, partialTick);
            renderBucket(neutral, 0.2f, infraredType, camera, partialTick);
        }

        // Restore vanilla blend/depth states:
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        lightTexture.turnOffLightLayer();

        // Cancel the original render() so vanilla’s code doesn’t also run:
        ci.cancel();
    }

    @Unique
    private void renderBucket(List<Particle> bucket, float heat, ParticleRenderType type, Camera camera, float partialTick) {
        if (bucket.isEmpty()) return;

        RenderSystem.setShader(() -> InfraredShader.INFRARED_PARTICLE_SHADER);
        var uniform = InfraredShader.INFRARED_PARTICLE_SHADER.safeGetUniform("u_Heat");
        uniform.set(heat);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = type.begin(tesselator, this.textureManager);
        if (bufferbuilder == null) return;

        for (Particle p : bucket) {
            try {
                p.render(bufferbuilder, camera, partialTick);
            } catch (Throwable throwable) {
                CrashReport report = CrashReport.forThrowable(throwable, "Rendering Particle Bucket");
                CrashReportCategory cat = report.addCategory("Particle");
                cat.setDetail("Particle", p::toString);
                cat.setDetail("Render Type", type::toString);
                throw new ReportedException(report);
            }
        }

        MeshData meshdata = bufferbuilder.build();
        if (meshdata != null) {
            BufferUploader.drawWithShader(meshdata);
        }
    }

    @Unique
    private static ParticleRenderType getParticleRenderType(ParticleRenderType vanillaType) {
        // If needed, you could guard this mapping with another “if(true)” or config‐flag.
        // For now, we always swap whenever VISOR mode is active:
        if (vanillaType == ParticleRenderType.TERRAIN_SHEET) {
            return InfraredShader.TERRAIN_SHEET;
        } else if (vanillaType == ParticleRenderType.PARTICLE_SHEET_OPAQUE) {
            return InfraredShader.PARTICLE_SHEET_OPAQUE;
        } else if (vanillaType == ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT) {
            return InfraredShader.PARTICLE_SHEET_TRANSLUCENT;
        } else if (vanillaType == ParticleRenderType.PARTICLE_SHEET_LIT) {
            return InfraredShader.PARTICLE_SHEET_LIT;
        } else if (vanillaType == ParticleRenderType.CUSTOM) {
            return InfraredShader.CUSTOM;
        }
        return InfraredShader.NO_RENDER;
    }
}
