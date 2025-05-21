package com.elfoteo.tutorialmod.event;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.elfoteo.tutorialmod.util.SuitUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

import java.util.*;

@EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ClientPowerJumpEvents {
    private static final float JUMP_COST = 20f;
    private static final Map<Player, Float> jumpChargeMap = new WeakHashMap<>();

    public static float getCurrentJumpCharge(Player player) {
        return jumpChargeMap.getOrDefault(player, 0f);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (Nanosuit.currentClientMode == SuitModes.NOT_EQUIPPED.get()) return;
        Player player = event.getEntity();
        if (player != Minecraft.getInstance().player) return;
        float charge = getCurrentJumpCharge(player);
        if (player.isCrouching() && player.onGround()) {
            charge = Math.min(1f, charge + (1f - charge) * 0.35f + 0.15f);

        } else {
            charge = Math.max(0f, charge - 0.1f);
        }
        jumpChargeMap.put(player, charge);
    }

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (Nanosuit.currentClientMode == SuitModes.NOT_EQUIPPED.get()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player != Minecraft.getInstance().player || !player.isCrouching()) return;
        float charge = getCurrentJumpCharge(player);
        if (!SuitUtils.tryDrainEnergy(player, JUMP_COST)) return;

        float pitch = player.getXRot() * (float)Math.PI / 180f;
        float yaw   = player.getYRot() * (float)Math.PI / 180f;
        float vY    = Mth.clamp((-Mth.sin(pitch) * 0.8f + 1f) * charge, 0f, Float.MAX_VALUE);
        float horiz = Mth.cos(pitch) * charge * 1.5f;
        Vec3 vel = player.getDeltaMovement();
        player.setDeltaMovement(
                vel.x + -Mth.sin(yaw) * horiz,
                vY,
                vel.z +  Mth.cos(yaw) * horiz
        );
        player.hasImpulse = true;
        jumpChargeMap.put(player, 0f);
    }

    private static void addVertex(VertexConsumer buffer, PoseStack.Pose pose,
                                  Vector3f pos, Vector3f normal,
                                  float r, float g, float b, float a) {
        buffer.addVertex(pose.pose(), pos.x(), pos.y(), pos.z())
                .setColor(r, g, b, a)
                .setNormal(pose, normal.x(), normal.y(), normal.z());
    }

    private static void renderTubeSegment(PoseStack poseStack, VertexConsumer buffer,
                                          Vector3f start, Vector3f end,
                                          int sides, float radius,
                                          float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        Vector3f axis = new Vector3f(end).sub(start);
        float length = axis.length();
        if (length < 1e-6f) return;
        axis.normalize();

        Vector3f tangent = (axis.z() != 0 || axis.x() != 0)
                ? new Vector3f(-axis.z(), 0, axis.x())
                : new Vector3f(1, 0, 0);
        tangent.normalize();
        Vector3f bitangent = new Vector3f();
        axis.cross(tangent, bitangent);
        bitangent.normalize();

        for (int i = 0; i < sides; i++) {
            float a1 = (float)(2*Math.PI*i/sides), a2 = (float)(2*Math.PI*(i+1)/sides);
            Vector3f off1 = new Vector3f(tangent).mul((float)Math.cos(a1)*radius)
                    .add(new Vector3f(bitangent).mul((float)Math.sin(a1)*radius));
            Vector3f off2 = new Vector3f(tangent).mul((float)Math.cos(a2)*radius)
                    .add(new Vector3f(bitangent).mul((float)Math.sin(a2)*radius));

            Vector3f v0 = new Vector3f(start).add(off1);
            Vector3f v1 = new Vector3f(start).add(off2);
            Vector3f v2 = new Vector3f(end).add(off2);
            Vector3f v3 = new Vector3f(end).add(off1);

            Vector3f n1 = new Vector3f(off1).normalize();
            Vector3f n2 = new Vector3f(off2).normalize();

            addVertex(buffer, pose, v0, n1, red, green, blue, alpha);
            addVertex(buffer, pose, v1, n2, red, green, blue, alpha);
            addVertex(buffer, pose, v2, n2, red, green, blue, alpha);

            addVertex(buffer, pose, v2, n2, red, green, blue, alpha);
            addVertex(buffer, pose, v3, n1, red, green, blue, alpha);
            addVertex(buffer, pose, v0, n1, red, green, blue, alpha);
        }
    }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (Nanosuit.currentClientMode == SuitModes.NOT_EQUIPPED.get()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level world = mc.level;
        if (world == null || player == null) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        float charge = getCurrentJumpCharge(player);
        if (charge <= 0f) return;

        double playerHeight = player.getBbHeight();
        // Start at body center instead of feet:
        Vec3 initialBody = player.position().add(0, playerHeight / 2, 0);
        Vec3 currentPos = initialBody;
        Vec3 renderPos = initialBody.subtract(camPos);

        // Use the exact same jump components as onLivingJump:
        float pitch = player.getXRot() * (float)Math.PI / 180f;
        float yaw   = player.getYRot() * (float)Math.PI / 180f;

        // vertical boost matches onLivingJump
        double vY    = Mth.clamp((-Mth.sin(pitch) * 0.8f + 1f) * charge, 0f, Double.MAX_VALUE);
        // horizontal magnitude matches onLivingJump
        double horiz = Mth.cos(pitch) * charge * 1.0f;

        // assemble the velocity vector
        Vec3 currentVel = new Vec3(
                -Math.sin(yaw) * horiz,
                vY,
                Math.cos(yaw) * horiz
        );

        // Constant values from Minecraft's physics
        final double GRAVITY = 0.08D;             // Base gravity value
        final double AIR_DRAG_HORIZONTAL = 0.91F; // Horizontal air resistance
        final double AIR_DRAG_VERTICAL = 0.98F;   // Vertical air resistance

        // Simulation parameters
        final int maxSteps = 200;

        List<Vec3> arc = new ArrayList<>();
        arc.add(renderPos);

        Vec3 landingPoint = null;

        // Simulate physics steps
        for (int i = 0; i < maxSteps; i++) {
            // Apply gravity first (matches Minecraft's physics order)
            currentVel = currentVel.subtract(0, GRAVITY, 0);

            // Apply air resistance (different for horizontal vs vertical components)
            currentVel = new Vec3(
                    currentVel.x * AIR_DRAG_HORIZONTAL,
                    currentVel.y * AIR_DRAG_VERTICAL,
                    currentVel.z * AIR_DRAG_HORIZONTAL
            );

            // Calculate next position
            Vec3 nextPos = currentPos.add(currentVel);

            // Check for ground collision
            int gx = Mth.floor(nextPos.x);
            int gz = Mth.floor(nextPos.z);
            double groundY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, gx, gz);
            if (nextPos.y <= groundY + 0.01) {
                landingPoint = new Vec3(nextPos.x, groundY, nextPos.z).subtract(camPos);
                arc.add(landingPoint);
                break;
            }

            // Check for block collisions (both feet and head level)
            Vec3 footStart = currentPos;
            Vec3 footEnd = nextPos;
            Vec3 headStart = currentPos.add(0, playerHeight, 0);
            Vec3 headEnd = nextPos.add(0, playerHeight, 0);
            BlockHitResult footHit = world.clip(new ClipContext(
                    footStart, footEnd,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
            ));
            BlockHitResult headHit = world.clip(new ClipContext(
                    headStart, headEnd,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
            ));
            BlockHitResult hit = footHit.getType() == HitResult.Type.BLOCK ? footHit : headHit.getType() == HitResult.Type.BLOCK ? headHit : null;
            if (hit != null) {
                landingPoint = hit.getLocation().subtract(camPos);
                arc.add(landingPoint);
                break;
            }

            currentPos = nextPos;
            arc.add(currentPos.subtract(camPos));
        }

        // If we didn't find a landing point, simulate falling straight down
        if (landingPoint == null) {
            Vec3 fallPos = currentPos;
            // Keep vertical velocity component for first fall step
            Vec3 fallVel = new Vec3(0, currentVel.y, 0);

            int fallSteps = 0;
            final int MAX_FALL_STEPS = 500; // Prevent infinite loops

            while (fallPos.y > world.getMinBuildHeight() && fallSteps < MAX_FALL_STEPS) {
                fallSteps++;

                // Apply gravity
                fallVel = fallVel.subtract(0, GRAVITY, 0);
                // Apply vertical air drag
                fallVel = new Vec3(0, fallVel.y * AIR_DRAG_VERTICAL, 0);

                Vec3 nextFallPos = fallPos.add(fallVel);

                int gx = Mth.floor(fallPos.x);
                int gz = Mth.floor(fallPos.z);
                double groundY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, gx, gz);
                if (nextFallPos.y <= groundY + 0.01) {
                    landingPoint = new Vec3(fallPos.x, groundY, fallPos.z).subtract(camPos);
                    arc.add(landingPoint);
                    break;
                }

                // Only add every few points to the rendering path to avoid cluttering
                if (fallSteps % 5 == 0) {
                    arc.add(nextFallPos.subtract(camPos));
                }

                fallPos = nextFallPos;
            }

            if (landingPoint == null && !arc.isEmpty()) {
                landingPoint = arc.get(arc.size() - 1);
            }
        }

        // Render the trajectory
        final int SIDES = 8;
        final float RADIUS = 0.02f;
        final float R = 0f, G = 0.7f, B = 1f, A = 0.3f;
        final float TARGET_R = 1f, TARGET_G = 0.2f, TARGET_B = 0.2f, TARGET_A = 0.5f;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        BufferBuilder buf = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR_NORMAL
        );
        poseStack.pushPose();

        // Render trajectory line
        for (int i = 0; i < arc.size() - 1; i++) {
            Vec3 p0 = arc.get(i), p1 = arc.get(i+1);

            // Gradually change color from yellow to red as we move along the path
            float progress = (float)i / (arc.size() - 1);
            float segR = R * (1 - progress) + TARGET_R * progress;
            float segG = G * (1 - progress) + TARGET_G * progress;
            float segB = B * (1 - progress) + TARGET_B * progress;

            renderTubeSegment(
                    poseStack, buf,
                    new Vector3f((float)p0.x, (float)p0.y, (float)p0.z),
                    new Vector3f((float)p1.x, (float)p1.y, (float)p1.z),
                    SIDES, RADIUS, segR, segG, segB, A
            );
        }

        // Render landing marker if we have one
        if (landingPoint != null) {
            Vector3f c = new Vector3f((float)landingPoint.x, (float)landingPoint.y, (float)landingPoint.z);
            float s = 0.3f;

            // Cross marker at landing point
            renderTubeSegment(poseStack, buf,
                    new Vector3f(c.x - s, c.y + 0.05f, c.z),
                    new Vector3f(c.x + s, c.y + 0.05f, c.z),
                    SIDES, RADIUS, TARGET_R, TARGET_G, TARGET_B, TARGET_A);
            renderTubeSegment(poseStack, buf,
                    new Vector3f(c.x, c.y + 0.05f, c.z - s),
                    new Vector3f(c.x, c.y + 0.05f, c.z + s),
                    SIDES, RADIUS, TARGET_R, TARGET_G, TARGET_B, TARGET_A);

            // Circle marker at landing point (approximated with an octagon)
            float ringRadius = 0.4f;
            int ringSegments = 8;
            for (int i = 0; i < ringSegments; i++) {
                float angle1 = (float)(2 * Math.PI * i / ringSegments);
                float angle2 = (float)(2 * Math.PI * (i + 1) / ringSegments);

                Vector3f p1 = new Vector3f(
                        c.x + ringRadius * (float)Math.cos(angle1),
                        c.y + 0.05f,
                        c.z + ringRadius * (float)Math.sin(angle1)
                );

                Vector3f p2 = new Vector3f(
                        c.x + ringRadius * (float)Math.cos(angle2),
                        c.y + 0.05f,
                        c.z + ringRadius * (float)Math.sin(angle2)
                );

                renderTubeSegment(poseStack, buf, p1, p2, SIDES, RADIUS, TARGET_R, TARGET_G, TARGET_B, TARGET_A);
            }
        }

        BufferUploader.drawWithShader(buf.build());
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        poseStack.popPose();
    }
}