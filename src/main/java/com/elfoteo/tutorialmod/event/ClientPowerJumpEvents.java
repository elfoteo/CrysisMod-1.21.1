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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
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

    private static final Set<UUID> approvedJumpers = new HashSet<>();


    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player != Minecraft.getInstance().player) return;

        if (Nanosuit.currentClientMode == SuitModes.NOT_EQUIPPED.get()) return;

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
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isCrouching()) return;
        if (Nanosuit.currentClientMode == SuitModes.NOT_EQUIPPED.get()) return;

        float charge = getCurrentJumpCharge(player);
        if (!SuitUtils.tryDrainEnergy(player, JUMP_COST)) return;

        approvedJumpers.add(player.getUUID()); // Allow client to perform jump
        jumpChargeMap.put(player, 0f);
    }
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player != Minecraft.getInstance().player) return;
        if (!player.isCrouching()) return;
        if (Nanosuit.currentClientMode == SuitModes.NOT_EQUIPPED.get()) return;

        float charge = getCurrentJumpCharge(player);

        float pitch = player.getXRot() * ((float)Math.PI / 180f);
        float yaw   = player.getYRot() * ((float)Math.PI / 180f);
        float vY    = Mth.clamp((-Mth.sin(pitch) * 0.8f + 1f) * charge, 0f, Float.MAX_VALUE);
        float horiz = Mth.cos(pitch) * charge * 1.5f;

        Vec3 vel = player.getDeltaMovement();
        player.setDeltaMovement(
                vel.x + -Mth.sin(yaw) * horiz,
                vY,
                vel.z + Mth.cos(yaw) * horiz
        );

        player.hasImpulse = true;
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

        // Player dimensions (not crouching)
        double playerWidth = 0.6; // Standard player width
        double playerHeight = 1.8; // Standard player height (not crouching)

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

            // Calculate next position with proper collision detection
            Vec3 nextPos = simulateMovementWithCollision(world, player, currentPos, currentVel, playerWidth, playerHeight);

            // Check if movement was blocked (collision occurred)
            Vec3 actualMovement = nextPos.subtract(currentPos);

            // Update velocity based on actual movement (handle axis-specific stopping)
            if (Math.abs(actualMovement.x) < Math.abs(currentVel.x) * 0.1) {
                currentVel = new Vec3(0, currentVel.y, currentVel.z); // Stop X movement
            }
            if (Math.abs(actualMovement.z) < Math.abs(currentVel.z) * 0.1) {
                currentVel = new Vec3(currentVel.x, currentVel.y, 0); // Stop Z movement
            }
            if (Math.abs(actualMovement.y) < Math.abs(currentVel.y) * 0.1) {
                currentVel = new Vec3(currentVel.x, 0, currentVel.z); // Stop Y movement
            }

            // Check for ground collision
            int gx = Mth.floor(nextPos.x);
            int gz = Mth.floor(nextPos.z);
            double groundY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, gx, gz);
            // Compare the player's feet with ground level, so nextPos.y - halfHeight for feet
            if (nextPos.y - playerHeight/2 <= groundY + 0.01) {
                // Set landing point exactly at ground level, no offsets
                landingPoint = new Vec3(nextPos.x, groundY, nextPos.z).subtract(camPos);
                arc.add(landingPoint);
                break;
            }

            currentPos = nextPos;
            arc.add(currentPos.subtract(camPos));

            // If velocity is essentially zero, we're stuck
            if (currentVel.lengthSqr() < 0.001) {
                break;
            }
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

                Vec3 nextFallPos = simulateMovementWithCollision(world, player, fallPos, fallVel, playerWidth, playerHeight);

                int gx = Mth.floor(fallPos.x);
                int gz = Mth.floor(fallPos.z);
                double groundY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, gx, gz);
                if (nextFallPos.y - playerHeight/2 <= groundY + 0.01) {
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
        RenderSystem.disableDepthTest();
        BufferBuilder buf = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR_NORMAL
        );
        poseStack.pushPose();

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
        }

        // Render the arc segments
        for (int i = 0; i < arc.size() - 1; i++) {
            Vector3f a = new Vector3f((float)arc.get(i).x, (float)arc.get(i).y, (float)arc.get(i).z);
            Vector3f b = new Vector3f((float)arc.get(i + 1).x, (float)arc.get(i + 1).y, (float)arc.get(i + 1).z);
            float alpha = ((float)i / arc.size());
            renderTubeSegment(poseStack, buf, a, b, SIDES, RADIUS, R, G, B, A * (1 - alpha));
        }

        poseStack.popPose();

        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }


    /**
     * Simulates movement with proper collision detection for the full player hitbox
     * Returns the actual position after collision resolution
     */
    private static Vec3 simulateMovementWithCollision(Level world, Player player, Vec3 currentPos, Vec3 velocity, double playerWidth, double playerHeight) {
        // Create AABB for player at current position
        double halfWidth = playerWidth / 2.0;
        double halfHeight = playerHeight / 2.0;

        Vec3 targetPos = currentPos.add(velocity);

        // Check collision for each axis separately (X, Y, Z)
        Vec3 resultPos = currentPos;

        // X-axis movement
        if (Math.abs(velocity.x) > 0.001) {
            Vec3 testPos = new Vec3(targetPos.x, resultPos.y, resultPos.z);
            if (hasntCollision(world, player, testPos, halfWidth, halfHeight)) {
                resultPos = testPos;
            }
        }

        // Y-axis movement
        if (Math.abs(velocity.y) > 0.001) {
            Vec3 testPos = new Vec3(resultPos.x, targetPos.y, resultPos.z);
            if (hasntCollision(world, player, testPos, halfWidth, halfHeight)) {
                resultPos = testPos;
            }
        }

        // Z-axis movement
        if (Math.abs(velocity.z) > 0.001) {
            Vec3 testPos = new Vec3(resultPos.x, resultPos.y, targetPos.z);
            if (hasntCollision(world, player, testPos, halfWidth, halfHeight)) {
                resultPos = testPos;
            }
        }

        return resultPos;
    }

    private static boolean hasntCollision(Level world, Player player, Vec3 pos, double halfWidth, double halfHeight) {
        // Create AABB for player at test position
        AABB playerBB = new AABB(
                pos.x - halfWidth, pos.y - halfHeight, pos.z - halfWidth,
                pos.x + halfWidth, pos.y + halfHeight, pos.z + halfWidth
        );

        // Get all block positions that could intersect with player
        int minX = Mth.floor(playerBB.minX);
        int maxX = Mth.floor(playerBB.maxX);
        int minY = Mth.floor(playerBB.minY);
        int maxY = Mth.floor(playerBB.maxY);
        int minZ = Mth.floor(playerBB.minZ);
        int maxZ = Mth.floor(playerBB.maxZ);

        // Check each block position for collision
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    BlockState blockState = world.getBlockState(blockPos);

                    if (!blockState.isAir()) {
                        VoxelShape blockShape = blockState.getCollisionShape(world, blockPos);
                        if (!blockShape.isEmpty()) {
                            // Transform block shape to world coordinates
                            AABB blockBB = blockShape.bounds().move(blockPos);

                            // Check if player AABB intersects with block AABB
                            if (playerBB.intersects(blockBB)) {
                                return false;
                            }
                        }
                    }
                }
            }
        }

        return true;
    }
}