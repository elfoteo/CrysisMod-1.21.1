package com.elfoteo.crysis.mixins.nanosuit.infrared;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.client.renderer.blockentity.SpawnerRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Objects;

@Mixin(SpawnerRenderer.class)
@OnlyIn(Dist.CLIENT)
public abstract class SpawnerRendererMixin implements BlockEntityRenderer<SignBlockEntity> {


    @Redirect(
            method = "renderEntityInSpawner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render"
                            + "(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            )
    )
    private static void redirectRenderIfVisor(
            EntityRenderDispatcher dispatcher,
            Entity entity,
            double x, double y, double z,
            float yRot,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource originalBuffers,
            int packedLight
    ) {
        // only draw if the player is NOT in VISOR mode
        if (Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE)
                != SuitModes.VISOR.get()) {
            // forward to vanilla renderer
            @SuppressWarnings("unchecked")
            EntityRenderer<Entity> renderer =
                    (EntityRenderer<Entity>) dispatcher.getRenderer(entity);
            renderer.render(entity, yRot, partialTicks, poseStack, originalBuffers, packedLight);
        }
        // else: skip drawing entirely
    }
}
