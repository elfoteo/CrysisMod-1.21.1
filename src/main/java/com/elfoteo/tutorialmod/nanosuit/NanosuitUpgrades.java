package com.elfoteo.tutorialmod.nanosuit;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.network.custom.ArmorInfoPacket;
import com.elfoteo.tutorialmod.network.custom.SuitModePacket;
import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.skill.SkillData;
import com.elfoteo.tutorialmod.util.SuitModes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

@EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class NanosuitUpgrades {
    private static final ResourceLocation boostResourceLocation = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "sprint_boost_nanosuit_upgrade");

    @SubscribeEvent()
    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();

        if (!(attacker instanceof Player player)) return;

        // Must be cloaked nanosuit mode
        if (player.getData(ModAttachments.SUIT_MODE) != SuitModes.CLOAK.get()) return;

        // Only apply for melee damage types
        if (!source.is(DamageTypes.PLAYER_ATTACK)
                && !source.is(DamageTypes.MOB_ATTACK)
                && !source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) return;

        // Player must have Ghost Titan skill unlocked
        if (!player.getData(ModAttachments.ALL_SKILLS)
                .get(Skill.GHOST_TITAN).isUnlocked()) return;

        // Add 30% bonus damage on melee attacks while cloaked
        float bonus = event.getAmount() * 1.3f;
        event.setAmount(bonus);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (SkillData.isUnlocked(Skill.SPRINT_BOOST, player)) {
            // Apply sprint boost effect
            // For example, modify player's movement speed attribute
            AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                double baseSpeed = 0.1;  // Default base speed, tweak as needed or get from attribute
                double boost = 0.2; // 20% boost
                double boostedSpeed = baseSpeed * (1 + boost);

                // Apply as modifier if not already applied
                if (speedAttr.getModifier(boostResourceLocation) == null) {
                    AttributeModifier sprintBoostModifier = new AttributeModifier(boostResourceLocation, boost, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                    speedAttr.addTransientModifier(sprintBoostModifier);
                }
            }
        } else {
            // Remove boost modifier if present
            AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.removeModifier(boostResourceLocation);
            }
        }
    }
}
