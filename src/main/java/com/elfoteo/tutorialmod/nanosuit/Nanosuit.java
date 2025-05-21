package com.elfoteo.tutorialmod.nanosuit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.keybindings.ModKeyBindings;
import com.elfoteo.tutorialmod.network.custom.SuitModePacket;
import com.elfoteo.tutorialmod.network.custom.ArmorInfoPacket;
import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.elfoteo.tutorialmod.util.SuitUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class Nanosuit {

    public static int currentClientMode = SuitModes.NOT_EQUIPPED.get();
    public static int previousClientMode = SuitModes.ARMOR.get();

    private static final float CLOAK_DRAIN_STILL   = 1.0f;
    private static final float CLOAK_DRAIN_MOVING  = 5.0f;
    private static final float CLOAK_DRAIN_RUNNING = 35.0f;
    private static final float VISOR_DRAIN_RATE    = 2.0f;

    private static final Map<UUID, Vec3> previousPositions = new HashMap<>();
    public static final Map<UUID, Long> cloakBreakTimestamps = new HashMap<>();

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // 1) bail out if suit not equipped
        if (!SuitUtils.isWearingFullNanosuit(player)) {
            if (currentClientMode != SuitModes.NOT_EQUIPPED.get()) {
                setClientMode(SuitModes.NOT_EQUIPPED.get(), player);
            }
            return;
        }

        float energy = player.getData(ModAttachments.ENERGY);
        int mode   = currentClientMode;
        int target = mode;

        long now        = System.currentTimeMillis();
        long blockedUntil = cloakBreakTimestamps.getOrDefault(player.getUUID(), 0L);
        boolean cloakBlocked = now < blockedUntil;

        boolean visorKeyDown = ModKeyBindings.VISOR_KEY.isDown();
        boolean cloakKeyDown = ModKeyBindings.CLOAK_KEY.isDown();

        // --- FIRST: VISOR takes absolute priority ---
        if (visorKeyDown && energy > 0f) {
            if (mode != SuitModes.VISOR.get()) {
                previousClientMode = mode;
                target = SuitModes.VISOR.get();
            }
        } else if (mode == SuitModes.VISOR.get()) {
            // Release visor ? back to whatever we were in before
            target = previousClientMode;
        }
        // --- THEN: only if we're NOT holding visor do we consider cloak ---
        else {
            if (cloakKeyDown && energy > 0f && mode != SuitModes.CLOAK.get() && !cloakBlocked) {
                target = SuitModes.CLOAK.get();
            } else if (!cloakKeyDown && mode == SuitModes.CLOAK.get()) {
                // When cloak key is released, drop back to ARMOR
                target = SuitModes.ARMOR.get();
            }
            // If you run out of energy in any non?ARMOR mode, auto?switch back:
            if (mode != SuitModes.ARMOR.get() && energy <= 0f) {
                target = SuitModes.ARMOR.get();
            }
        }

        if (target != mode) {
            setClientMode(target, player);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void setClientMode(int newMode, Player player) {
        int oldMode = currentClientMode;
        currentClientMode = newMode;
        player.setData(ModAttachments.SUIT_MODE, newMode);
        PacketDistributor.sendToServer(new SuitModePacket(player.getId(), newMode));

        // Only reload lighting once, if we're entering or exiting VISOR
        if (oldMode == SuitModes.VISOR.get() || newMode == SuitModes.VISOR.get()) {
            Minecraft.getInstance().levelRenderer.allChanged();
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void reloadClientLighting() {
        LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
        renderer.allChanged();
    }

    @SubscribeEvent
    public static void onPlayerServerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        if (!SuitUtils.isWearingFullNanosuit(player)) {
            if (player.getData(ModAttachments.SUIT_MODE) != SuitModes.NOT_EQUIPPED.get()) {
                player.setData(ModAttachments.SUIT_MODE, SuitModes.NOT_EQUIPPED.get());
                PacketDistributor.sendToPlayer((ServerPlayer) player,
                        new SuitModePacket(player.getId(), SuitModes.NOT_EQUIPPED.get()));
            }
            return;
        }

        int mode = player.getData(ModAttachments.SUIT_MODE);
        float energy = player.getData(ModAttachments.ENERGY);
        int maxEnergy = player.getData(ModAttachments.MAX_ENERGY);
        float newEnergy = energy;
        int newMode = mode;

        long currentTime = System.currentTimeMillis();
        long blockedUntil = cloakBreakTimestamps.getOrDefault(player.getUUID(), 0L);
        boolean cloakBlocked = currentTime < blockedUntil;

        if ((mode == SuitModes.CLOAK.get() || mode == SuitModes.VISOR.get()) && energy > 0f) {
            if (mode == SuitModes.CLOAK.get() && cloakBlocked) {
                newMode = SuitModes.ARMOR.get();
                player.setData(ModAttachments.SUIT_MODE, newMode);
                player.removeEffect(MobEffects.INVISIBILITY);
            } else {
                float drain = (mode == SuitModes.CLOAK.get() ? getCloakDrainRate(player) : VISOR_DRAIN_RATE) / 20f;
                newEnergy = Math.max(0f, energy - drain);
                player.setData(ModAttachments.ENERGY, newEnergy);

                if (mode == SuitModes.CLOAK.get()) {
                    player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5, 0, true, false));
                }

                if (newEnergy <= 0f) {
                    newMode = SuitModes.ARMOR.get();
                    player.setData(ModAttachments.SUIT_MODE, newMode);
                    player.removeEffect(MobEffects.INVISIBILITY);
                }
            }
        }

        previousPositions.put(player.getUUID(), player.position());

        if (mode != newMode || energy != newEnergy) {
            PacketDistributor.sendToPlayer((ServerPlayer) player,
                    new SuitModePacket(player.getId(), newMode));
            PacketDistributor.sendToPlayer((ServerPlayer) player,
                    new ArmorInfoPacket(
                            newEnergy,
                            maxEnergy,
                            player.getData(ModAttachments.MAX_ENERGY_REGEN),
                            newMode
                    ));
        }
    }

    private static float getCloakDrainRate(Player player) {
        UUID id = player.getUUID();
        Vec3 cur = player.position();
        Vec3 prev = previousPositions.getOrDefault(id, cur);
        double dx = cur.x - prev.x;
        double dz = cur.z - prev.z;
        if (player.isSprinting()) return CLOAK_DRAIN_RUNNING;
        if (dx * dx + dz * dz > 0.001) return CLOAK_DRAIN_MOVING;
        if (player.getData(ModAttachments.ALL_SKILLS).get(Skill.DYNAMIC_CLOAKING).isUnlocked()){
            return CLOAK_DRAIN_STILL / 2;
        }
        return CLOAK_DRAIN_STILL;
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        Entity entityAttacker = source.getEntity();
        if (entityAttacker instanceof Player attacker) {
            int attackerMode = attacker.getData(ModAttachments.SUIT_MODE);
            boolean attackerInCloak = attackerMode == SuitModes.CLOAK.get();
            boolean attackerHasGhostKill = attacker.getData(ModAttachments.ALL_SKILLS).get(Skill.GHOST_KILL).isUnlocked();
            boolean targetWillDie = target.getHealth() - event.getNewDamage() <= 0 || target.isDeadOrDying();

            if (attackerInCloak) {
                if (!(attackerHasGhostKill && targetWillDie)) {
                    attacker.setData(ModAttachments.SUIT_MODE, SuitModes.ARMOR.get());
                    attacker.removeEffect(MobEffects.INVISIBILITY);

                    PacketDistributor.sendToAllPlayers(new SuitModePacket(attacker.getId(), SuitModes.ARMOR.get()));

                    long cooldown = attackerHasGhostKill ? 500 : 1000;
                    cloakBreakTimestamps.put(attacker.getUUID(), System.currentTimeMillis() + cooldown);
                }
            }
        }

        if (target instanceof Player targetPlayer){
            if (targetPlayer.getData(ModAttachments.SUIT_MODE) == SuitModes.CLOAK.get()){
                targetPlayer.setData(ModAttachments.SUIT_MODE, SuitModes.ARMOR.get());
                // Reduce energy because the player got hit while in cloak mode
                //SuitUtils.tryDrainEnergy(targetPlayer, 20);
                targetPlayer.removeEffect(MobEffects.INVISIBILITY);
                int cooldown = 1000;
                cloakBreakTimestamps.put(targetPlayer.getUUID(), System.currentTimeMillis() + cooldown);

                PacketDistributor.sendToAllPlayers(new SuitModePacket(targetPlayer.getId(), SuitModes.ARMOR.get()));
            }
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player target) || target.level().isClientSide()) return;
        if (target.getData(ModAttachments.SUIT_MODE) != SuitModes.ARMOR.get()) return;

        float originalDamage = event.getAmount();
        float reducedDamage = SuitUtils.absorbDamage(target, originalDamage);
        event.setAmount(reducedDamage);
    }
}
