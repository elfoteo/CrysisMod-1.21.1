// src/main/java/com/elfoteo/tutorialmod/mixins/ModelBlockRenderMixin.java
package com.elfoteo.crysis.mixins;

import com.elfoteo.crysis.event.ModClientEvents;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.util.SolidColoredVertexConsumer;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRenderMixin {

    @Inject(method = "putQuadData", at = @At("HEAD"), cancellable = true)
    public void putQuadData(
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            VertexConsumer consumer,
            PoseStack.Pose pose,
            BakedQuad quad,
            float b0, float b1, float b2, float b3,
            int lm0, int lm1, int lm2, int lm3,
            int packedOverlay,
            CallbackInfo ci) {
        // Only run when visor is active
        if (Nanosuit.currentClientMode != SuitModes.VISOR.get()) {
            return;
        }
        ClientLevel clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null) {
            return;
        }

        // 1) Compute raw intensity this quad
        float maxRaw = 0f;
        for (int packed : new int[]{lm0, lm1, lm2, lm3}) {
            int bl = packed & 0xF, sl = (packed >> 4) & 0xF;
            maxRaw = Math.max(maxRaw, Math.max(bl, sl) / 15f);
        }

        // Apply exponential decay falloff to maxRaw (less light -> exponentially smaller)
        float lambda = 4.0f; // adjust for sharpness
        maxRaw = (float) Math.exp(-lambda * (1f - maxRaw));

        // 3) Fetch computed heat
        float heat;
        if (ModClientEvents.FAST_COMPUTE){
            heat = maxRaw;
        } else {
            heat = ModClientEvents.getHeat(pos.immutable(), maxRaw);
            if (heat <= 0f) {
                heat = 0f;
            }
        }

        // 4) Apply thermal coloring and render custom
        float red   = heat;
        float green = 0f;
        float blue  = 1f - heat;
        float alpha = 1f;

        if (!(consumer instanceof SolidColoredVertexConsumer)) {
            consumer = new SolidColoredVertexConsumer(
                    consumer,
                    (int)(red   * 255),
                    (int)(green * 255),
                    (int)(blue  * 255),
                    (int)(alpha * 255)
            );
        }
        consumer.putBulkData(
                pose, quad,
                new float[]{ b0, b1, b2, b3 },
                red, green, blue, alpha,
                new int[]{ 255, 255, 255, 255 },
                packedOverlay,
                false
        );
        ci.cancel();
    }
}