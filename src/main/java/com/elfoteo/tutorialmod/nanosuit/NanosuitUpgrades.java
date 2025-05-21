package com.elfoteo.tutorialmod.nanosuit;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.skill.SkillState;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.elfoteo.tutorialmod.util.SuitUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.util.Map;

@EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class NanosuitUpgrades {
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.getData(ModAttachments.SUIT_MODE) == SuitModes.NOT_EQUIPPED.get()) return;

        Map<Skill, SkillState> skills = player.getData(ModAttachments.ALL_SKILLS);
        SkillState state = skills.get(Skill.GROUND_SKIM);
        if (state == null || !state.isUnlocked()) return;

        if (!player.level().isClientSide()) {
            float fallDamage = event.getDistance() * event.getDamageMultiplier();
            float remaining = SuitUtils.absorbFallDamage(player, fallDamage);

            if (remaining <= 0.01f) {
                event.setCanceled(true); // Fully absorbed
            } else {
                event.setDamageMultiplier(remaining / event.getDistance()); // Apply remaining damage
            }
        }
    }
}
