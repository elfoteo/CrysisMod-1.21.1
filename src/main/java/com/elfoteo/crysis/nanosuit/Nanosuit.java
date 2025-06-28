package com.elfoteo.crysis.nanosuit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.keybindings.ModKeyBindings;
import com.elfoteo.crysis.network.custom.SuitModePacket;
import com.elfoteo.crysis.network.custom.ArmorInfoPacket;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.util.SuitModes;
import com.elfoteo.crysis.util.SuitUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = CrysisMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class Nanosuit {

    public static int currentClientMode = SuitModes.NOT_EQUIPPED.get();
    public static int previousClientMode = SuitModes.ARMOR.get();

    private static final float CLOAK_DRAIN_STILL   = 1.0f;
    private static final float CLOAK_DRAIN_MOVING  = 5.0f;
    private static final float CLOAK_DRAIN_RUNNING = 35.0f;
    private static final float VISOR_DRAIN_RATE    = 2.0f;

    private static final Map<UUID, Vec3> previousPositions = new HashMap<>();
    public static final Map<UUID, Long> cloakBreakTimestamps = new HashMap<>();

    // ─── NEW FIELDS ──────────────────────────────────────────────────────────────────
    /**
     * Tracks whether the visor key was down on the previous tick, so we can detect “held” vs “just pressed.”
     */
    private static boolean previousVisorKeyDown = false;
    /**
     * Same for the cloak key.
     */
    private static boolean previousCloakKeyDown = false;
    // ────────────────────────────────────────────────────────────────────────────────

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // If not wearing full nanosuit, force mode back to NOT_EQUIPPED
        if (!SuitUtils.isWearingFullNanosuit(player)) {
            if (currentClientMode != SuitModes.NOT_EQUIPPED.get()) {
                setClientMode(SuitModes.NOT_EQUIPPED.get(), player);
            }
            // Reset “previous key” states so when they equip again, keys are “fresh.”
            previousVisorKeyDown = false;
            previousCloakKeyDown = false;
            return;
        }

        // ─── Gather essential data ─────────────────────────────────────────────────
        float energy    = player.getData(ModAttachments.ENERGY);
        int   mode      = currentClientMode;
        int   target    = mode;
        int   maxEnergy = player.getData(ModAttachments.MAX_ENERGY);
        float threshold = 0.1f * maxEnergy;
        // 10% of maxEnergy; only allow reactivation if energy >= threshold (unless key was just pressed)

        long now            = System.currentTimeMillis();
        long blockedUntil   = cloakBreakTimestamps.getOrDefault(player.getUUID(), 0L);
        boolean cloakBlocked = now < blockedUntil;

        boolean visorKeyDown = ModKeyBindings.VISOR_KEY.isDown();
        boolean cloakKeyDown = ModKeyBindings.CLOAK_KEY.isDown();

        // Detect “just pressed” vs “held down”
        boolean justPressedVisor = visorKeyDown && !previousVisorKeyDown;
        boolean justPressedCloak = cloakKeyDown && !previousCloakKeyDown;
        // ──────────────────────────────────────────────────────────────────────────

        // VISOR logic
        if (visorKeyDown && energy > 0f) {
            // If currently not in VISOR, try to switch in, but only if:
            //    • The player “just pressed” the VISOR key, OR
            //    • Player has at least 10% energy
            if (mode != SuitModes.VISOR.get()) {
                if (justPressedVisor || energy >= threshold) {
                    previousClientMode = mode;
                    target = SuitModes.VISOR.get();
                }
            }
        } else if (mode == SuitModes.VISOR.get()) {
            // If player has let go of the key (or energy is 0), revert back to previous mode
            target = previousClientMode;
        } else {
            // ─── CLOAK logic when visor is not active ──────────────────────────────
            if (cloakKeyDown && energy > 0f && mode != SuitModes.CLOAK.get() && !cloakBlocked) {
                // Only enter CLOAK if:
                //    • “Just pressed” CLOAK key, OR
                //    • energy >= 10% threshold
                if (justPressedCloak || energy >= threshold) {
                    target = SuitModes.CLOAK.get();
                }
            } else if (!cloakKeyDown && mode == SuitModes.CLOAK.get()) {
                // Player released the cloak key → go back to ARMOR
                target = SuitModes.ARMOR.get();
            }

            // If the player is in CLOAK (or VISOR) and energy hits zero, force ARMOR immediately
            if (mode != SuitModes.ARMOR.get() && energy <= 0f) {
                target = SuitModes.ARMOR.get();
            }
        }

        // If we need to switch mode, do it now
        if (target != mode) {
            setClientMode(target, player);
        }

        // ─── Update “previous key state” for next tick ─────────────────────────────
        previousVisorKeyDown = visorKeyDown;
        previousCloakKeyDown = cloakKeyDown;
        // ──────────────────────────────────────────────────────────────────────────
    }

    @OnlyIn(Dist.CLIENT)
    public static void setClientMode(int newMode, Player player, boolean sendPacket) {
        if (currentClientMode == newMode) return;

        currentClientMode = newMode;
        player.setData(ModAttachments.SUIT_MODE, newMode);
        System.out.println(newMode + "; sendPacket=" + sendPacket);

        if (sendPacket) {
            PacketDistributor.sendToServer(new SuitModePacket(player.getId(), newMode));
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void setClientMode(int newMode, Player player) {
        setClientMode(newMode, player, true);
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

        int   mode      = player.getData(ModAttachments.SUIT_MODE);
        float energy    = player.getData(ModAttachments.ENERGY);
        int   maxEnergy = player.getData(ModAttachments.MAX_ENERGY);
        float newEnergy = energy;
        int   newMode   = mode;

        long currentTime    = System.currentTimeMillis();
        long blockedUntil   = cloakBreakTimestamps.getOrDefault(player.getUUID(), 0L);
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
                    // Energy just dropped to zero → force ARMOR on server side as well
                    newMode = SuitModes.ARMOR.get();
                    player.setData(ModAttachments.SUIT_MODE, newMode);
                    player.removeEffect(MobEffects.INVISIBILITY);
                }
            }
        }

        previousPositions.put(player.getUUID(), player.position());

        if (mode != newMode || energy != newEnergy) {
            //PacketDistributor.sendToPlayer((ServerPlayer) player,
            //        new SuitModePacket(player.getId(), newMode));
            PacketDistributor.sendToPlayer((ServerPlayer) player,
                    new ArmorInfoPacket(
                            newEnergy,
                            maxEnergy,
                            player.getData(ModAttachments.MAX_ENERGY_REGEN),
                            newMode
                    ));
        }
        PacketDistributor.sendToAllPlayers(new SuitModePacket(player.getId(), newMode));
    }

    private static float getCloakDrainRate(Player player) {
        UUID id = player.getUUID();
        Vec3 cur = player.position();
        Vec3 prev = previousPositions.getOrDefault(id, cur);
        double dx = cur.x - prev.x;
        double dz = cur.z - prev.z;
        boolean dynamicCloaking = player.getData(ModAttachments.ALL_SKILLS).get(Skill.DYNAMIC_CLOAKING).isUnlocked();
        if (player.isSprinting()) return CLOAK_DRAIN_RUNNING * (dynamicCloaking ? 0.75f : 1);
        if (dx * dx + dz * dz > 0.001) return CLOAK_DRAIN_MOVING * (dynamicCloaking ? 0.75f : 1);
        return CLOAK_DRAIN_STILL * (dynamicCloaking ? 0.75f : 1);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        Entity entityAttacker = source.getEntity();
        if (entityAttacker instanceof Player attacker) {
            int attackerMode = attacker.getData(ModAttachments.SUIT_MODE);
            boolean attackerInCloak = attackerMode == SuitModes.CLOAK.get();
            boolean attackerHasGhostKill = attacker.getData(ModAttachments.ALL_SKILLS).get(Skill.GHOST_KILL).isUnlocked();
            boolean attackerHasPredatoryStrike = attacker.getData(ModAttachments.ALL_SKILLS).get(Skill.PREDATORY_STRIKE).isUnlocked();
            boolean targetWillDie = target.getHealth() - event.getNewDamage() <= 0 || target.isDeadOrDying();

            if (attackerInCloak) {
                if (!(attackerHasGhostKill && targetWillDie)) {
                    attacker.setData(ModAttachments.SUIT_MODE, SuitModes.ARMOR.get());
                    attacker.removeEffect(MobEffects.INVISIBILITY);

                    PacketDistributor.sendToAllPlayers(new SuitModePacket(attacker.getId(), SuitModes.ARMOR.get()));

                    long cooldown = attackerHasGhostKill ? 500 : 1000;
                    cloakBreakTimestamps.put(attacker.getUUID(), System.currentTimeMillis() + cooldown);
                }

                // Handle energy refund from PREDATORY_STRIKE
                if (attackerHasPredatoryStrike && targetWillDie) {
                    float currentEnergy = attacker.getData(ModAttachments.ENERGY);
                    float maxEnergy = attacker.getData(ModAttachments.MAX_ENERGY);
                    float newEnergy = Math.min(maxEnergy, currentEnergy + 10f);
                    attacker.setData(ModAttachments.ENERGY, newEnergy);

                    PacketDistributor.sendToPlayer((ServerPlayer) attacker,
                            new ArmorInfoPacket(
                                    newEnergy,
                                    (int) maxEnergy,
                                    attacker.getData(ModAttachments.MAX_ENERGY_REGEN),
                                    attacker.getData(ModAttachments.SUIT_MODE)
                            ));
                }
            }
        }

        if (target instanceof Player targetPlayer) {
            if (targetPlayer.getData(ModAttachments.SUIT_MODE) == SuitModes.CLOAK.get()) {
                targetPlayer.setData(ModAttachments.SUIT_MODE, SuitModes.ARMOR.get());
                targetPlayer.removeEffect(MobEffects.INVISIBILITY);
                int cooldown = 1000;
                cloakBreakTimestamps.put(targetPlayer.getUUID(), System.currentTimeMillis() + cooldown);

                PacketDistributor.sendToAllPlayers(new SuitModePacket(targetPlayer.getId(), SuitModes.ARMOR.get()));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player target) || target.level().isClientSide()) return;
        if (target.getData(ModAttachments.SUIT_MODE) != SuitModes.ARMOR.get()) return;

        float originalDamage = event.getAmount();
        float reducedDamage = SuitUtils.absorbDamage(target, originalDamage, event.getSource());
        event.setAmount(reducedDamage);
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR && event.getEntity() instanceof Player player) {
            if (SuitUtils.isWearingFullNanosuit(player)) {
                player.setData(ModAttachments.SUIT_MODE, SuitModes.ARMOR.get());
                if (player.level().isClientSide) {
                    currentClientMode = SuitModes.ARMOR.get();
                } else {
                    PacketDistributor.sendToPlayer((ServerPlayer) player, new SuitModePacket(
                            player.getId(),
                            SuitModes.ARMOR.get()
                    ));
                }
            }
        }
    }
}
