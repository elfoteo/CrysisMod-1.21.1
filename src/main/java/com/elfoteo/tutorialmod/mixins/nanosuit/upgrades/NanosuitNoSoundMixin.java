package com.elfoteo.tutorialmod.mixins.nanosuit.upgrades;

import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.skill.SkillState;
import com.elfoteo.tutorialmod.util.SuitModes;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * This mixin cancels sound events that are about to be dispatched for entities that have the shadow effect.
 */
@Mixin(ServerLevel.class)
public abstract class NanosuitNoSoundMixin {
    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), cancellable = true)
    private void cancelSoundFromInvisibleEntity(Player player, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed, CallbackInfo ci) {
        if (player == null) return;
        if (player.getData(ModAttachments.SUIT_MODE) != SuitModes.CLOAK.get()) return;
        boolean hasSkillUnlocked = false;
        List<SkillState> skills = player.getData(ModAttachments.ALL_SKILLS);
        for (SkillState skill : skills){
            if (skill.getSkill() == Skill.SILENT_STEP){
                hasSkillUnlocked = true;
                break;
            }
        }

        if (hasSkillUnlocked) {
            // Cancel dispatching the sound event.
            ci.cancel();
        }
    }
}
