package com.elfoteo.tutorialmod.mixins.nanosuit.upgrades;

import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.skill.SkillState;
import com.elfoteo.tutorialmod.util.SuitModes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(Entity.class)
public class NanosuitNoVibrations {
    @Inject(method = "dampensVibrations", at = @At("HEAD"), cancellable = true)
    private void noVibrations(CallbackInfoReturnable<Boolean> cir) {
        if (shouldCancelSound()){
            cir.setReturnValue(true);
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
