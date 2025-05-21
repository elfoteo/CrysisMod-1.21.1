package com.elfoteo.tutorialmod.mixins.layer;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.event.ClientPowerJumpEvents;
import com.elfoteo.tutorialmod.nanosuit.Nanosuit;
import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.util.InfraredShader;
import com.elfoteo.tutorialmod.util.RenderState;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.elfoteo.tutorialmod.util.SuitUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>> {
    @Unique
    private static final ResourceLocation OVERLAY_TEXTURE = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID,
            "textures/models/armor/nanosuit_overlay.png");

    @Inject(method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;ILnet/minecraft/resources/ResourceLocation;)V",
            at = @At("HEAD"), cancellable = true)
    private void onRenderModel(PoseStack poseStack,
                               MultiBufferSource buffer,
                               int packedLight,
                               Model model,
                               int dyeColor,
                               ResourceLocation textureLocation,
                               CallbackInfo ci) {
        Player player = RenderState.currentlyRenderingPlayer;
        if (player != null && SuitUtils.isWearingFullNanosuit(player)) {
            int modeVal = player.getData(ModAttachments.SUIT_MODE);
            SuitModes mode = SuitModes.from(modeVal);

            if (!RenderState.isRenderingMainPlayer && mode == SuitModes.CLOAK) {
                ci.cancel();
                return;
            }

            float currentEnergy = player.getData(ModAttachments.ENERGY);
            int maxEnergy = player.getData(ModAttachments.MAX_ENERGY);
            float energyRatio = maxEnergy > 0 ? (float) currentEnergy / maxEnergy : 0f;

            // Access main client player for special visor mode handling
            Player mcPlayer = Minecraft.getInstance().player;
            boolean mcPlayerInVisor = mcPlayer != null && mcPlayer.getData(ModAttachments.SUIT_MODE) == SuitModes.VISOR.get();

            float r, g, b;
            if (!mcPlayerInVisor){
                if (mode == SuitModes.VISOR) {
                    // In visor mode, fixed colors depending on THERMAL_DAMPENERS skill on current player
                    r = 1f; g = 0f; b = 0f;
                } else {
                    // Other modes
                    switch (mode) {
                        case CLOAK:
                            r = 0.6f; g = 0.8f; b = 1f;
                            break;
                        case ARMOR:
                        default:
                            r = 0.5f; g = 0.5f; b = 0.5f;
                            break;
                    }

                    // Only apply pulsing red overlay if energy low AND mc.player is NOT in visor mode
                    if (energyRatio < 0.2f) {
                        double millis = System.currentTimeMillis();
                        float pulse = (float) ((Math.sin(millis / 300.0) + 1.0) * 0.5);
                        r = r * (1 - pulse) + 1f * pulse;
                        g = g * (1 - pulse) + 0f * pulse;
                        b = b * (1 - pulse) + 0f * pulse;
                    }
                }
            }
            else{
                if (player.getData(ModAttachments.ALL_SKILLS).get(Skill.THERMAL_DAMPENERS).isUnlocked()){
                    r = 0.235f; g = 0f; b = 0.157f;
                }
                else{
                    r = 1f; g = 0f; b = 0f;
                }
            }

            // If player is charging a jump, fade to yellow (1, 1, 0) based on charge
            if (player.level().isClientSide) {
                float jumpCharge = ClientPowerJumpEvents.getCurrentJumpCharge(player);
                if (jumpCharge > 0f) {
                    float targetR = 1f, targetG = 1f, targetB = 0f;
                    r = Mth.lerp(jumpCharge, r, targetR);
                    g = Mth.lerp(jumpCharge, g, targetG);
                    b = Mth.lerp(jumpCharge, b, targetB);
                }
            }

            float timeSec = (System.currentTimeMillis() % 100000L) / 1000f;
            InfraredShader.NANOSUIT_OVERLAY_SHADER.getUniform("Time").set(timeSec);
            InfraredShader.NANOSUIT_OVERLAY_SHADER.getUniform("ModeModulator").set(r, g, b, 1f);

            VertexConsumer vertexConsumer = buffer.getBuffer(InfraredShader.nanosuitOverlay(OVERLAY_TEXTURE));
            model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, dyeColor);
            ci.cancel();
        } else {
            if (Nanosuit.currentClientMode == SuitModes.VISOR.get()) {
                VertexConsumer vertexConsumer = buffer.getBuffer(InfraredShader.infraredArmorCutoutNoCull(textureLocation));
                model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, dyeColor);
            }
        }
    }
}
