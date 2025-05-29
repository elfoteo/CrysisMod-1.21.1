// src/main/java/com/elfoteo/tutorialmod/mixins/ModelBlockRenderMixin.java
package com.elfoteo.crysis.mixins;

import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRenderMixin {
    @Shadow protected abstract void vertex(VertexConsumer buffer, float x, float y, float z, float red, float green, float blue, float u, float v, int packedLight);
    @Shadow
    protected abstract void vertex(
            VertexConsumer buffer,
            float x,
            float y,
            float z,
            float red,
            float green,
            float blue,
            float alpha,
            float u,
            float v,
            int packedLight
    );

    @Shadow
    private static boolean isFaceOccludedByNeighbor(BlockGetter level, BlockPos pos, Direction side, float height, BlockState blockState) {
        return false;
    }

    @Shadow protected abstract float calculateAverageHeight(BlockAndTintGetter level, Fluid fluid, float currentHeight, float height1, float height2, BlockPos pos);

    @Shadow
    public static boolean shouldRenderFace(BlockAndTintGetter level, BlockPos pos, FluidState fluidState, BlockState selfState, Direction direction, BlockState otherState) {
        return false;
    }

    @Shadow
    private static boolean isNeighborStateHidingOverlay(FluidState selfState, BlockState otherState, Direction neighborFace) {
        return false;
    }

    @Shadow protected abstract float getHeight(BlockAndTintGetter level, Fluid fluid, BlockPos pos, BlockState blockState, FluidState fluidState);



    @Inject(
            method = "tesselate",
            at = @At("HEAD"),
            cancellable = true
    )
    public void modifyFluidColor(BlockAndTintGetter level, BlockPos pos, VertexConsumer buffer, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        if (Nanosuit.currentClientMode != SuitModes.VISOR.get()) return;
        boolean isLava = fluidState.is(FluidTags.LAVA);
        TextureAtlasSprite[] atextureatlassprite = FluidSpriteCache.getFluidSprites(level, pos, fluidState);
        float alpha = isLava? 1f: 0.7f;
        float red = isLava? 1f: 0f;
        float green = isLava? 0.5f: 0f;
        float blue = isLava? 0.5f: 0.42f;
        BlockState blockstate = level.getBlockState(pos.relative(Direction.DOWN));
        BlockState blockstate1 = level.getBlockState(pos.relative(Direction.UP));
        BlockState blockstate2 = level.getBlockState(pos.relative(Direction.NORTH));
        FluidState fluidstate2 = blockstate2.getFluidState();
        BlockState blockstate3 = level.getBlockState(pos.relative(Direction.SOUTH));
        FluidState fluidstate3 = blockstate3.getFluidState();
        BlockState blockstate4 = level.getBlockState(pos.relative(Direction.WEST));
        FluidState fluidstate4 = blockstate4.getFluidState();
        BlockState blockstate5 = level.getBlockState(pos.relative(Direction.EAST));
        FluidState fluidstate5 = blockstate5.getFluidState();
        boolean flag1 = !isNeighborStateHidingOverlay(fluidState, blockstate1, Direction.DOWN);

        boolean flag2 = shouldRenderFace(level, pos, fluidState, blockState, Direction.DOWN, blockstate) && !isFaceOccludedByNeighbor(level, pos, Direction.DOWN, 0.8888889F, blockstate);
        boolean flag3 = shouldRenderFace(level, pos, fluidState, blockState, Direction.NORTH, blockstate2);
        boolean flag4 = shouldRenderFace(level, pos, fluidState, blockState, Direction.SOUTH, blockstate3);
        boolean flag5 = shouldRenderFace(level, pos, fluidState, blockState, Direction.WEST, blockstate4);
        boolean flag6 = shouldRenderFace(level, pos, fluidState, blockState, Direction.EAST, blockstate5);
        if (flag1 || flag2 || flag6 || flag5 || flag3 || flag4) {
            float f3 = level.getShade(Direction.DOWN, true);
            float f4 = level.getShade(Direction.UP, true);
            float f5 = level.getShade(Direction.NORTH, true);
            float f6 = level.getShade(Direction.WEST, true);
            Fluid fluid = fluidState.getType();
            float f11 = this.getHeight(level, fluid, pos, blockState, fluidState);

            float f7;
            float f8;
            float f9;
            float f10;
            if (f11 >= 1.0F) {
                f7 = 1.0F;
                f8 = 1.0F;
                f9 = 1.0F;
                f10 = 1.0F;
            } else {
                float f12 = this.getHeight(level, fluid, pos.north(), blockstate2, fluidstate2);
                float f13 = this.getHeight(level, fluid, pos.south(), blockstate3, fluidstate3);
                float f14 = this.getHeight(level, fluid, pos.east(), blockstate5, fluidstate5);
                float f15 = this.getHeight(level, fluid, pos.west(), blockstate4, fluidstate4);
                f7 = this.calculateAverageHeight(level, fluid, f11, f12, f14, pos.relative(Direction.NORTH).relative(Direction.EAST));
                f8 = this.calculateAverageHeight(level, fluid, f11, f12, f15, pos.relative(Direction.NORTH).relative(Direction.WEST));
                f9 = this.calculateAverageHeight(level, fluid, f11, f13, f14, pos.relative(Direction.SOUTH).relative(Direction.EAST));
                f10 = this.calculateAverageHeight(level, fluid, f11, f13, f15, pos.relative(Direction.SOUTH).relative(Direction.WEST));
            }

            int overallLight = 0xE00070;

            float f36 = (float)(pos.getX() & 15);
            float f37 = (float)(pos.getY() & 15);
            float f38 = (float)(pos.getZ() & 15);
            float f16 = flag2 ? 0.001F : 0.0F;
            if (flag1 && !isFaceOccludedByNeighbor(level, pos, Direction.UP, Math.min(Math.min(f8, f10), Math.min(f9, f7)), blockstate1)) {
                f8 -= 0.001F;
                f10 -= 0.001F;
                f9 -= 0.001F;
                f7 -= 0.001F;
                float f57 = f4 * red;
                float f29 = f4 * green;
                float f30 = f4 * blue;
                this.vertex(buffer, f36 + 0.0F, f37 + f8, f38 + 0.0F, f57, f29, f30, alpha, 0f, 0f, overallLight);
                this.vertex(buffer, f36 + 0.0F, f37 + f10, f38 + 1.0F, f57, f29, f30, alpha, 0f, 0f, overallLight);
                this.vertex(buffer, f36 + 1.0F, f37 + f9, f38 + 1.0F, f57, f29, f30, alpha, 0f, 0f, overallLight);
                this.vertex(buffer, f36 + 1.0F, f37 + f7, f38 + 0.0F, f57, f29, f30, alpha, 0f, 0f, overallLight);
                if (fluidState.shouldRenderBackwardUpFace(level, pos.above())) {
                    this.vertex(buffer, f36 + 0.0F, f37 + f8, f38 + 0.0F, f57, f29, f30, alpha, 0f, 0f, overallLight);
                    this.vertex(buffer, f36 + 1.0F, f37 + f7, f38 + 0.0F, f57, f29, f30, alpha, 0f, 0f, overallLight);
                    this.vertex(buffer, f36 + 1.0F, f37 + f9, f38 + 1.0F, f57, f29, f30, alpha, 0f, 0f, overallLight);
                    this.vertex(buffer, f36 + 0.0F, f37 + f10, f38 + 1.0F, f57, f29, f30, alpha, 0f, 0f, overallLight);
                }
            }


            if (flag2) {
                float f46 = f3 * red;
                float f48 = f3 * green;
                float f50 = f3 * blue;
                this.vertex(buffer, f36, f37 + f16, f38 + 1.0F, f46, f48, f50, alpha, 0f, 0f, overallLight);
                this.vertex(buffer, f36, f37 + f16, f38, f46, f48, f50, alpha, 0f, 0f, overallLight);
                this.vertex(buffer, f36 + 1.0F, f37 + f16, f38, f46, f48, f50, alpha, 0f, 0f, overallLight);
                this.vertex(buffer, f36 + 1.0F, f37 + f16, f38 + 1.0F, f46, f48, f50, alpha, 0f, 0f, overallLight);
            }

            for(Direction direction : Direction.Plane.HORIZONTAL) {
                float f44;
                float f45;
                float f47;
                float f49;
                float f51;
                float f52;
                boolean flag7;
                switch (direction) {
                    case NORTH:
                        f44 = f8;
                        f45 = f7;
                        f47 = f36;
                        f51 = f36 + 1.0F;
                        f49 = f38 + 0.001F;
                        f52 = f38 + 0.001F;
                        flag7 = flag3;
                        break;
                    case SOUTH:
                        f44 = f9;
                        f45 = f10;
                        f47 = f36 + 1.0F;
                        f51 = f36;
                        f49 = f38 + 1.0F - 0.001F;
                        f52 = f38 + 1.0F - 0.001F;
                        flag7 = flag4;
                        break;
                    case WEST:
                        f44 = f10;
                        f45 = f8;
                        f47 = f36 + 0.001F;
                        f51 = f36 + 0.001F;
                        f49 = f38 + 1.0F;
                        f52 = f38;
                        flag7 = flag5;
                        break;
                    default:
                        f44 = f7;
                        f45 = f9;
                        f47 = f36 + 1.0F - 0.001F;
                        f51 = f36 + 1.0F - 0.001F;
                        f49 = f38;
                        f52 = f38 + 1.0F;
                        flag7 = flag6;
                }

                if (flag7 && !isFaceOccludedByNeighbor(level, pos, direction, Math.max(f44, f45), level.getBlockState(pos.relative(direction)))) {
                    BlockPos blockpos = pos.relative(direction);
                    TextureAtlasSprite textureatlassprite2 = atextureatlassprite[1];
                    if (atextureatlassprite[2] != null && level.getBlockState(blockpos).shouldDisplayFluidOverlay(level, blockpos, fluidState)) {
                        textureatlassprite2 = atextureatlassprite[2];
                    }

                    float f32 = direction.getAxis() == Direction.Axis.Z ? f5 : f6;
                    float f33 = f4 * f32 * red;
                    float f34 = f4 * f32 * green;
                    float f35 = f4 * f32 * blue;
                    this.vertex(buffer, f47, f37 + f44, f49, f33, f34, f35, alpha, 0f, 0f, overallLight);
                    this.vertex(buffer, f51, f37 + f45, f52, f33, f34, f35, alpha, 0f, 0f, overallLight);
                    this.vertex(buffer, f51, f37 + f16, f52, f33, f34, f35, alpha, 0f, 0f, overallLight);
                    this.vertex(buffer, f47, f37 + f16, f49, f33, f34, f35, alpha, 0f, 0f, overallLight);
                    if (textureatlassprite2 != atextureatlassprite[2]) {
                        this.vertex(buffer, f47, f37 + f16, f49, f33, f34, f35, alpha, 0f, 0f, overallLight);
                        this.vertex(buffer, f51, f37 + f16, f52, f33, f34, f35, alpha, 0f, 0f, overallLight);
                        this.vertex(buffer, f51, f37 + f45, f52, f33, f34, f35, alpha, 0f, 0f, overallLight);
                        this.vertex(buffer, f47, f37 + f44, f49, f33, f34, f35, alpha, 0f, 0f, overallLight);
                    }
                }
            }
        }
        ci.cancel();
    }
}