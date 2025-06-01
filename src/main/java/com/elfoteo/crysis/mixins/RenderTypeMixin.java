package com.elfoteo.crysis.mixins;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BrightnessCombiner;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(RenderType.class)
public abstract class RenderTypeMixin {
    @Inject(method = "outline(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;", at=@At("HEAD"), cancellable = true)
    private static void outline(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        cir.setReturnValue(InfraredShader.CompositeRenderType.OUTLINE.apply(location, RenderType.NO_CULL));
    }

    @Inject(method = "entitySolid", at=@At("HEAD"), cancellable = true)
    private static void entitySolid(ResourceLocation location, CallbackInfoReturnable<RenderType> cir) {
        if (InfraredShader.INFRARED_RENDER_TYPE_ENTITY_GENERIC == null) return;
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) != SuitModes.VISOR.get()) return;
        cir.setReturnValue(InfraredShader.infraredEntityGeneric(location));
    }
}
