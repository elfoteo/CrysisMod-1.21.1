// src/main/java/com/elfoteo/crysis/client/renderer/FlagBlockEntityRenderer.java
package com.elfoteo.crysis.block.entity.renderer;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.block.entity.FlagBlockEntity;
import com.elfoteo.crysis.flag.Team;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Renders a beacon‐style beam above the FlagBlockEntity.
 * The base beam color is always determined by the flag’s owner:
 *   - No owner: gray (0xAAAAAA)
 *   - Blue owner: blue (0x5555FF)
 *   - Red owner: red (0xFF5555)
 * If there are only Blue‐team players on top, the beam briefly flashes aqua (0x00FFFF)
 * once every 5 seconds (for 0.5 seconds), then returns to the base color.
 * If there are only Red‐team players on top, the beam briefly flashes dark red (0xAA0000)
 * once every 5 seconds (for 0.5 seconds), then returns to the base color.
 * In any other situation (mixed teams on top or no players on top), the beam remains the base color.
 */
public class FlagBlockEntityRenderer implements BlockEntityRenderer<FlagBlockEntity> {
    private static final ResourceLocation BEAM_LOCATION =
            ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "textures/entity/flag_beam.png");

    public FlagBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        // No extra setup needed
    }

    @Override
    public void render(FlagBlockEntity flagBE, float partialTicks, PoseStack stack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = flagBE.getLevel();
        if (level == null) return;

        BlockPos pos = flagBE.getBlockPos();
        int flagY = pos.getY();
        int yOffset = 0;
        int maxBuild = level.getMaxBuildHeight();
        int height = maxBuild - (flagY + 1);

        // 1) Count players directly above the flag block within radius 6.
        int blueCount = 0;
        int redCount = 0;
        AABB box = new AABB(
                pos.getX() - 6, flagY + 1, pos.getZ() - 6,
                pos.getX() + 7, level.getMaxBuildHeight(), pos.getZ() + 7
        );

        List<Player> players = level.getEntitiesOfClass(Player.class, box);
        for (Player p : players) {
            Vec3 feetPos = p.position();
            if (feetPos.y <= flagY) continue;
            double dx = feetPos.x - (pos.getX() + 0.5);
            double dz = feetPos.z - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz <= 36.0 && p.getTeam() != null) {
                String teamName = p.getTeam().getName();
                if (teamName.equalsIgnoreCase("blue")) {
                    blueCount++;
                } else if (teamName.equalsIgnoreCase("red")) {
                    redCount++;
                }
            }
        }

        // 2) Determine the base beam color based on flag owner.
        final int BASE_GRAY = 0xAAAAAA;
        final int BASE_BLUE = 0x5555FF;
        final int BASE_RED = 0xAA0000;

        int baseColor;
        switch (flagBE.getOwner()) {
            case RED -> baseColor = BASE_RED;
            case BLUE -> baseColor = BASE_BLUE;
            default -> baseColor = BASE_GRAY;
        }

        // 3) Flash settings
        final int FLASH_AQUA     = 0x00FFFF;
        final int FLASH_DARK_RED = 0xFF4444;

        // We'll flash once every 5 seconds (100 ticks), with a flash duration of 0.5 seconds (10 ticks).
        final long CYCLE_TICKS = 80L;  // 4 seconds at 20 ticks/sec
        final long FLASH_TICKS = 20L;   // 1 second at 20 ticks/sec

        boolean onlyBluePlayers = (blueCount > 0 && redCount == 0);
        boolean onlyRedPlayers  = (redCount > 0 && blueCount == 0);

        int beamColor = baseColor;

        if (onlyBluePlayers || onlyRedPlayers) {
            long gameTime = level.getGameTime();
            long tickInCycle = gameTime % CYCLE_TICKS;

            if (tickInCycle < FLASH_TICKS) {
                // Compute an intensity from 0→1→0 over FLASH_TICKS.
                float subTick = (tickInCycle + partialTicks) / (float) FLASH_TICKS;
                float intensity = (float) Math.sin(subTick * Math.PI);

                int targetFlash = onlyBluePlayers ? FLASH_AQUA : FLASH_DARK_RED;

                int rBase = (baseColor >> 16) & 0xFF;
                int gBase = (baseColor >> 8) & 0xFF;
                int bBase = baseColor & 0xFF;

                int rFlash = (targetFlash >> 16) & 0xFF;
                int gFlash = (targetFlash >> 8) & 0xFF;
                int bFlash = targetFlash & 0xFF;

                int r = (int) (rBase + (rFlash - rBase) * intensity);
                int g = (int) (gBase + (gFlash - gBase) * intensity);
                int b = (int) (bBase + (bFlash - bBase) * intensity);

                beamColor = (r << 16) | (g << 8) | b;
            }
            // else: outside FLASH_TICKS, beamColor remains baseColor
        }

        // 4) Render the beacon beam with the computed beamColor.
        renderBeaconBeam(stack, bufferSource, BEAM_LOCATION,
                partialTicks, 1.0F, level.getGameTime(),
                yOffset, height, beamColor, 0.2F, 0.25F);
    }

    /**
     * Copied (with minor visibility adjustments) from Minecraft’s BeaconRenderer.
     * Renders the colored, animated beam.
     */
    private static void renderBeaconBeam(PoseStack poseStack, MultiBufferSource bufferSource,
                                         ResourceLocation beamLocation, float partialTick,
                                         float textureScale, long gameTime,
                                         int yOffset, int height, int color,
                                         float beamRadius, float glowRadius) {
        int i = yOffset + height;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        float f = (float)Math.floorMod(gameTime, 40L) + partialTick;
        float f1 = height < 0 ? f : -f;
        float f2 = Mth.frac(f1 * 0.2F - (float)Mth.floor(f1 * 0.1F));
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(f * 2.25F - 45.0F));
        float f3;
        float f5;
        float f6 = -beamRadius;
        float f9 = -beamRadius;
        float f12 = -1.0F + f2;
        float f13 = (float)height * textureScale * (0.5F / beamRadius) + f12;
        renderPart(poseStack, bufferSource.getBuffer(RenderType.beaconBeam(beamLocation, false)),
                color, yOffset, i, 0.0F, beamRadius, beamRadius, 0.0F,
                f6, 0.0F, 0.0F, f9, 0.0F, 1.0F, f13, f12);
        poseStack.popPose();
        f3 = -glowRadius;
        float f4 = -glowRadius;
        f5 = -glowRadius;
        f6 = -glowRadius;
        f12 = -1.0F + f2;
        f13 = (float)height * textureScale + f12;
        renderPart(poseStack, bufferSource.getBuffer(RenderType.beaconBeam(beamLocation, true)),
                FastColor.ARGB32.color(32, color), yOffset, i,
                f3, f4, glowRadius, f5, f6, glowRadius, glowRadius, glowRadius,
                0.0F, 1.0F, f13, f12);
        poseStack.popPose();
    }

    private static void renderPart(PoseStack poseStack, VertexConsumer consumer, int color,
                                   int minY, int maxY, float x1, float z1, float x2, float z2,
                                   float x3, float z3, float x4, float z4,
                                   float minU, float maxU, float minV, float maxV) {
        PoseStack.Pose posestackPose = poseStack.last();
        renderQuad(posestackPose, consumer, color, minY, maxY, x1, z1, x2, z2, minU, maxU, minV, maxV);
        renderQuad(posestackPose, consumer, color, minY, maxY, x4, z4, x3, z3, minU, maxU, minV, maxV);
        renderQuad(posestackPose, consumer, color, minY, maxY, x2, z2, x4, z4, minU, maxU, minV, maxV);
        renderQuad(posestackPose, consumer, color, minY, maxY, x3, z3, x1, z1, minU, maxU, minV, maxV);
    }

    private static void renderQuad(PoseStack.Pose pose, VertexConsumer consumer, int color,
                                   int minY, int maxY, float minX, float minZ,
                                   float maxX, float maxZ, float minU, float maxU,
                                   float minV, float maxV) {
        addVertex(pose, consumer, color, maxY, minX, minZ, maxU, minV);
        addVertex(pose, consumer, color, minY, minX, minZ, maxU, maxV);
        addVertex(pose, consumer, color, minY, maxX, maxZ, minU, maxV);
        addVertex(pose, consumer, color, maxY, maxX, maxZ, minU, minV);
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer consumer, int color,
                                  int y, float x, float z, float u, float v) {
        consumer.addVertex(pose, x, (float)y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public boolean shouldRenderOffScreen(FlagBlockEntity blockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(FlagBlockEntity blockEntity, Vec3 cameraPos) {
        Vec3 center = Vec3.atCenterOf(blockEntity.getBlockPos());
        Vec3 cameraXZ = new Vec3(cameraPos.x, center.y, cameraPos.z);
        return center.distanceTo(cameraXZ) < (double) getViewDistance();
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(FlagBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, BeaconRenderer.MAX_RENDER_Y, pos.getZ() + 1.0);
    }
}
