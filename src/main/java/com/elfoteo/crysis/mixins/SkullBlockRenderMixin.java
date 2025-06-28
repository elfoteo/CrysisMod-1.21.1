package com.elfoteo.crysis.mixins;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.mixins.accessors.ICompositeStateAccessor;
import com.elfoteo.crysis.mixins.accessors.TextureStateShardAccessor;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.util.ICompositeRenderType;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.SuitModes;
import com.elfoteo.crysis.util.TrailTextureManager;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
@Mixin(SkullBlockRenderer.class)
public abstract class SkullBlockRenderMixin {

    @Shadow @Final public static Map<SkullBlock.Type, ResourceLocation> SKIN_BY_TYPE;

    @Unique
    private static RenderType getRenderType(SkullBlock.Type type, ResolvableProfile profile) {
        if (Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) == SuitModes.VISOR.get()){
            ResourceLocation resourcelocation = SKIN_BY_TYPE.get(type);
            if (type == SkullBlock.Types.PLAYER && profile != null){
                SkinManager skinmanager = Minecraft.getInstance().getSkinManager();
                return InfraredShader.infraredEntityTranslucent(skinmanager.getInsecureSkin(profile.gameProfile()).texture());
            }
            else {
                return InfraredShader.infraredEntityCutoutNoCullZOffset(resourcelocation);
            }
        }
        return SkullBlockRenderer.getRenderType(type, profile);
    }

    // Inject into the getRenderType method (this looks correct)
    @Inject(method = "getRenderType",
            at = @At("HEAD"), cancellable = true)
    private static void getRenderType(SkullBlock.Type type, ResolvableProfile profile, CallbackInfoReturnable<RenderType> cir) {
        if (Minecraft.getInstance().player.getData(ModAttachments.SUIT_MODE) == SuitModes.VISOR.get()){
            ResourceLocation resourcelocation = SKIN_BY_TYPE.get(type);
            if (type == SkullBlock.Types.PLAYER && profile != null){
                SkinManager skinmanager = Minecraft.getInstance().getSkinManager();
                cir.setReturnValue(InfraredShader.infraredEntityTranslucent(skinmanager.getInsecureSkin(profile.gameProfile()).texture()));
            }
            else {
                cir.setReturnValue(InfraredShader.infraredEntityCutoutNoCullZOffset(resourcelocation));
            }
        }
    }
}