package com.elfoteo.tutorialmod.nanosuit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.keybindings.ModKeyBindings;
import com.elfoteo.tutorialmod.network.custom.SuitModePacket;
import com.elfoteo.tutorialmod.network.custom.ArmorInfoPacket;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.elfoteo.tutorialmod.util.SuitUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class Nanosuit {

    // Default to NOT_EQUIPPED
    public static int currentClientMode = SuitModes.NOT_EQUIPPED.get();
    public static int previousClientMode = SuitModes.ARMOR.get();

    private static final float CLOAK_DRAIN_STILL   = 1.0f;
    private static final float CLOAK_DRAIN_MOVING  = 5.0f;
    private static final float CLOAK_DRAIN_RUNNING = 35.0f;
    private static final float VISOR_DRAIN_RATE    = 2.0f;

    private static final Map<UUID, Vec3> previousPositions = new HashMap<>();

    /**
     * Client-side tick: handle input and mode switching (hold for cloak & visor).
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // If not wearing full nanosuit, force NOT_EQUIPPED
        if (!SuitUtils.isWearingFullNanosuit(player)) {
            if (currentClientMode != SuitModes.NOT_EQUIPPED.get()) {
                setClientMode(SuitModes.NOT_EQUIPPED.get(), player);
            }
            return;
        }

        float energy = player.getData(ModAttachments.ENERGY);
        int mode = currentClientMode;
        int target = mode;

        // If no energy while in cloak/visor, switch back to ARMOR
        if ((mode == SuitModes.CLOAK.get() || mode == SuitModes.VISOR.get()) && energy <= 0f) {
            target = SuitModes.ARMOR.get();
        } else {
            // Hold cloak key to enter
            if (ModKeyBindings.CLOAK_KEY.isDown()) {
                if (energy > 0f && mode != SuitModes.CLOAK.get()) {
                    target = SuitModes.CLOAK.get();
                }
            } else if (mode == SuitModes.CLOAK.get()) {
                // release cloak
                target = SuitModes.ARMOR.get();
            }

            // Hold visor key to enter
            if (ModKeyBindings.VISOR_KEY.isDown()) {
                if (energy > 0f && mode != SuitModes.VISOR.get()) {
                    previousClientMode = mode;
                    target = SuitModes.VISOR.get();
                    reloadClientLighting();
                }
            } else if (mode == SuitModes.VISOR.get()) {
                // release visor
                target = previousClientMode;
                reloadClientLighting();
            }
        }

        // Sync if changed
        if (target != mode) {
            setClientMode(target, player);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void setClientMode(int mode, Player player) {
        currentClientMode = mode;
        player.setData(ModAttachments.SUIT_MODE, mode);
        PacketDistributor.sendToServer(new SuitModePacket(player.getId(), mode));
        if (mode == SuitModes.VISOR.get()) {
            reloadClientLighting();
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void reloadClientLighting() {
        LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
        renderer.allChanged();
    }

    /**
     * Server-side tick: handle energy drain and auto-reset on depletion.
     */
    @SubscribeEvent
    public static void onPlayerServerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        // If not wearing full nanosuit, force NOT_EQUIPPED
        if (!SuitUtils.isWearingFullNanosuit(player)) {
            if (player.getData(ModAttachments.SUIT_MODE) != SuitModes.NOT_EQUIPPED.get()) {
                player.setData(ModAttachments.SUIT_MODE, SuitModes.NOT_EQUIPPED.get());
                // Inform client
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

        if (mode == SuitModes.CLOAK.get() || mode == SuitModes.VISOR.get()) {
            float drain = (mode == SuitModes.CLOAK.get() ? getCloakDrainRate(player) : VISOR_DRAIN_RATE) / 20f;
            newEnergy = Math.max(0f, energy - drain);
            player.setData(ModAttachments.ENERGY, newEnergy);

            if (mode == SuitModes.CLOAK.get()) {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5, 0, true, false));
            }

            if (newEnergy <= 0f) {
                newMode = SuitModes.ARMOR.get();
                player.setData(ModAttachments.SUIT_MODE, newMode);
            }
        }

        previousPositions.put(player.getUUID(), player.position());

        if (mode != newMode || energy != newEnergy) {
            // Send mode update
            PacketDistributor.sendToPlayer((ServerPlayer) player,
                    new SuitModePacket(player.getId(), newMode));
            // Send energy + armor info update
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
        return CLOAK_DRAIN_STILL;
    }

    /**
     * Handle incoming damage: absorb via suit, and break cloak on hit like in Crysis 3.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (event.getSource().is(DamageTypes.FALL)) return;
        if (!(entity instanceof Player p) || p.level().isClientSide()) return;
        if (currentClientMode == SuitModes.NOT_EQUIPPED.get()) return;

        int mode = p.getData(ModAttachments.SUIT_MODE);
        if (mode == SuitModes.CLOAK.get()) {
            // break cloak
            p.setData(ModAttachments.SUIT_MODE, SuitModes.ARMOR.get());
            p.setData(ModAttachments.ENERGY, 0f);
            PacketDistributor.sendToPlayer((ServerPlayer) p,
                    new SuitModePacket(p.getId(), SuitModes.ARMOR.get()));
        }
        event.setAmount(SuitUtils.absorbDamage(p, event.getAmount()));
    }
}
