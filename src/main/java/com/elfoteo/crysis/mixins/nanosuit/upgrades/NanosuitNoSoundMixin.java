package com.elfoteo.crysis.mixins.nanosuit.upgrades;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillState;
import com.elfoteo.crysis.util.SuitModes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(Entity.class)
public abstract class NanosuitNoSoundMixin {

    @Inject(method = "playMuffledStepSound", at = @At("HEAD"), cancellable = true)
    private void cancelMuffledSound(BlockState state, BlockPos pos, CallbackInfo ci) {
        if (shouldCancelSound()) {
            ci.cancel();
        }
    }

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void cancelStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (shouldCancelSound()) {
            ci.cancel();
        }
    }

    @Inject(method = "playCombinationStepSounds", at = @At("HEAD"), cancellable = true)
    private void cancelComboSound(BlockState primaryState, BlockState secondaryState, BlockPos primaryPos, BlockPos secondaryPos, CallbackInfo ci) {
        if (shouldCancelSound()) {
            ci.cancel();
        }
    }

    @Unique
    private boolean shouldCancelSound() {
        if (!((Entity) (Object) this instanceof Player player)) return false;
        if (player.getData(ModAttachments.SUIT_MODE) != SuitModes.CLOAK.get()) return false;

        Map<Skill, SkillState> skills = player.getData(ModAttachments.ALL_SKILLS);
        SkillState state = skills.get(Skill.SILENT_STEP);
        return state != null && state.isUnlocked();
    }
}
