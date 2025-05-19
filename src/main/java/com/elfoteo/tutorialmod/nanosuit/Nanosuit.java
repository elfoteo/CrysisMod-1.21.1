package com.elfoteo.tutorialmod.nanosuit;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.keybindings.ModKeyBindings;
import com.elfoteo.tutorialmod.network.custom.SuitModePacket;
import com.elfoteo.tutorialmod.network.custom.ArmorInfoPacket;
import com.elfoteo.tutorialmod.util.SetSectionRenderDispatcher;
import com.elfoteo.tutorialmod.util.SuitModes;
import com.elfoteo.tutorialmod.util.SuitUtils;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class Nanosuit {

    public static int currentClientMode = SuitModes.ARMOR.ordinal();
    public static int previousClientMode = SuitModes.ARMOR.ordinal();

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

        // If not wearing full nanosuit, force armor mode
        if (!SuitUtils.isWearingFullNanosuit(player)) {
            setClientMode(SuitModes.ARMOR.ordinal(), player);
            return;
        }

        float energy = player.getData(ModAttachments.ENERGY);
        int mode = currentClientMode;
        int target = mode;

        // Prevent re-entry if no energy
        if ((mode == SuitModes.CLOAK.ordinal() || mode == SuitModes.VISOR.ordinal()) && energy <= 0f) {
            target = SuitModes.ARMOR.ordinal();
        } else {
            // Hold cloak key to enter
            if (ModKeyBindings.CLOAK_KEY.isDown()) {
                if (energy > 0f && mode != SuitModes.CLOAK.ordinal()) {
                    target = SuitModes.CLOAK.ordinal();
                }
            } else if (mode == SuitModes.CLOAK.ordinal()) {
                // release cloak
                target = SuitModes.ARMOR.ordinal();
            }

            // Hold visor key to enter
            if (ModKeyBindings.VISOR_KEY.isDown()) {
                if (energy > 0f && mode != SuitModes.VISOR.ordinal()) {
                    previousClientMode = mode;
                    target = SuitModes.VISOR.ordinal();
                    reloadClientLighting();
                }
            } else if (mode == SuitModes.VISOR.ordinal()) {
                // release visor
                target = previousClientMode;
                reloadClientLighting();
            }
        }

        // Sync client/server if changed
        if (target != mode) {
            setClientMode(target, player);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void setClientMode(int mode, Player player) {
        currentClientMode = mode;
        player.setData(ModAttachments.SUIT_MODE, mode);
        PacketDistributor.sendToServer(new SuitModePacket(player.getId(), mode));
        // ensure lighting update on mode changes that affect visuals
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

        // Ensure armor if no suit
        if (!SuitUtils.isWearingFullNanosuit(player)) {
            player.setData(ModAttachments.SUIT_MODE, SuitModes.ARMOR.ordinal());
            return;
        }

        int mode = player.getData(ModAttachments.SUIT_MODE);
        float energy = player.getData(ModAttachments.ENERGY);
        int maxEnergy = player.getData(ModAttachments.MAX_ENERGY);
        float newEnergy = energy;
        int newMode = mode;

        if (mode == SuitModes.CLOAK.ordinal() || mode == SuitModes.VISOR.ordinal()) {
            float drain = (mode == SuitModes.CLOAK.ordinal() ? getCloakDrainRate(player) : VISOR_DRAIN_RATE) / 20f;
            newEnergy = Math.max(0f, energy - drain);
            player.setData(ModAttachments.ENERGY, newEnergy);

            if (mode == SuitModes.CLOAK.ordinal()) {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5, 0, true, false));
            }

            if (newEnergy <= 0f) {
                newMode = SuitModes.ARMOR.ordinal();
                player.setData(ModAttachments.SUIT_MODE, newMode);
            }
        }

        previousPositions.put(player.getUUID(), player.position());

        if (mode != newMode || energy != newEnergy) {
            // Inform client: mode then stats
            PacketDistributor.sendToPlayer((ServerPlayer) player,
                    new SuitModePacket(player.getId(), newMode)
            );
            PacketDistributor.sendToPlayer((ServerPlayer) player,
                    new ArmorInfoPacket(
                            newEnergy,
                            maxEnergy,
                            player.getData(ModAttachments.MAX_ENERGY_REGEN),
                            newMode
                    )
            );
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
        if (!(entity instanceof Player p) || p.level().isClientSide()) return;

        int mode = p.getData(ModAttachments.SUIT_MODE);
        if (mode == SuitModes.CLOAK.ordinal()) {
            // break cloak
            p.setData(ModAttachments.SUIT_MODE, SuitModes.ARMOR.ordinal());
            p.setData(ModAttachments.ENERGY, 0f);
            PacketDistributor.sendToPlayer((ServerPlayer) p,
                    new SuitModePacket(p.getId(), SuitModes.ARMOR.ordinal())
            );
        }
        event.setAmount(SuitUtils.absorbDamage(p, event.getAmount()));
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player p && !p.level().isClientSide()) {
            if (SuitUtils.absorbFallDamage(p, event.getDistance() * event.getDamageMultiplier())) {
                event.setCanceled(true);
            }
        }
    }
}
