package com.elfoteo.tutorialmod.nanosuit;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReferenceArray;

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
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
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

    // Publicly expose current mode for easy access
    public static int currentClientMode = SuitModes.ARMOR.ordinal();
    public static int previousClientMode = SuitModes.ARMOR.ordinal();

    // Energy drain constants (per second)
    private static final float CLOAK_DRAIN_STILL   = 1.0f;
    private static final float CLOAK_DRAIN_MOVING  = 5.0f;
    private static final float CLOAK_DRAIN_RUNNING = 35.0f;
    private static final float VISOR_DRAIN_RATE    = 2.0f;

    private static boolean cloakPressedLast = false;
    private static final Map<UUID, Vec3> previousPositions = new HashMap<>();

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // must be wearing full nanosuit
        if (!SuitUtils.isWearingFullNanosuit(player)) {
            if (currentClientMode != SuitModes.ARMOR.ordinal()) {
                currentClientMode = SuitModes.ARMOR.ordinal();
                PacketDistributor.sendToServer(new SuitModePacket(player.getId(), currentClientMode));
            }
            return;
        }

        int mode = currentClientMode;
        int target = mode;

        // toggle cloak on G press
        boolean cloakDown = ModKeyBindings.CLOAK_KEY.isDown();
        if (cloakDown && !cloakPressedLast) {
            target = (mode == SuitModes.CLOAK.ordinal()
                    ? SuitModes.ARMOR.ordinal()
                    : SuitModes.CLOAK.ordinal());
        }
        cloakPressedLast = cloakDown;

        // handle visor hold
        if (ModKeyBindings.VISOR_KEY.isDown()) {
            // entering visor: save previous
            if (mode != SuitModes.VISOR.ordinal()) {
                previousClientMode = mode;
                target = SuitModes.VISOR.ordinal();
                if (player.level() instanceof ClientLevel clientLevel) {
                    reloadClientLighting(clientLevel);
                }
            }

        } else if (mode == SuitModes.VISOR.ordinal()) {
            // release: revert
            if (target != previousClientMode){
                if (player.level() instanceof ClientLevel clientLevel) {
                    reloadClientLighting(clientLevel);
                }
            }
            target = previousClientMode;
        }

        // apply change
        if (target != mode) {
            currentClientMode = target;
            player.setData(ModAttachments.SUIT_MODE, target);
            PacketDistributor.sendToServer(new SuitModePacket(player.getId(), target));
        }
    }

    private static SectionRenderDispatcher getSectionRenderDispatcher(LevelRenderer levelRenderer) {
        try {
            // The field is private, so use reflection
            var field = LevelRenderer.class.getDeclaredField("sectionRenderDispatcher");
            field.setAccessible(true);
            return (SectionRenderDispatcher) field.get(levelRenderer);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void reloadClientLighting(ClientLevel level) {
        //level.getChunkSource().getLightEngine().runLightUpdates();
        //Minecraft.getInstance().level.clearTintCaches();
        Minecraft.getInstance().levelRenderer.allChanged();

        LevelRenderer renderer = Minecraft.getInstance().levelRenderer;

        // Use interface mixin to access setSectionRenderDispatcher
        if (renderer.getSectionRenderDispatcher() == null) {
            ((SetSectionRenderDispatcher) renderer).setSectionRenderDispatcher(new SectionRenderDispatcher(
                    Minecraft.getInstance().level,
                    renderer,
                    Util.backgroundExecutor(),
                    ((SetSectionRenderDispatcher) renderer).getRenderBuffers(),
                    Minecraft.getInstance().getBlockRenderer(),
                    Minecraft.getInstance().getBlockEntityRenderDispatcher()
            ));
        } else {
            renderer.getSectionRenderDispatcher().setLevel(Minecraft.getInstance().level);
        }
    }


    @SubscribeEvent
    public static void onPlayerServerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        if (!SuitUtils.isWearingFullNanosuit(player)) {
            if (player.getData(ModAttachments.SUIT_MODE) != SuitModes.ARMOR.ordinal()) {
                player.setData(ModAttachments.SUIT_MODE, SuitModes.ARMOR.ordinal());
            }
            return;
        }

        int mode = player.getData(ModAttachments.SUIT_MODE);
        float energy = player.getData(ModAttachments.ENERGY);
        int maxEnergy = player.getData(ModAttachments.MAX_ENERGY);
        float newEnergy = energy;
        int newMode = mode;

        if (mode == SuitModes.CLOAK.ordinal() || mode == SuitModes.VISOR.ordinal()) {
            if (energy > 0) {
                if (mode == SuitModes.CLOAK.ordinal()) {
                    //player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 2, 0, true, false));
                    float drainTick = getCloakDrainRate(player) / 20f;
                    newEnergy = Math.max(0, energy - drainTick);
                } else {
                    float drainTick = VISOR_DRAIN_RATE / 20f;
                    newEnergy = Math.max(0, energy - drainTick);
                }
                player.setData(ModAttachments.ENERGY, newEnergy);
            } else {
                newMode = SuitModes.ARMOR.ordinal();
                player.setData(ModAttachments.SUIT_MODE, newMode);
            }
        }

        previousPositions.put(player.getUUID(), player.position());


        if (mode != newMode || energy != newEnergy) {
            PacketDistributor.sendToPlayer((ServerPlayer) player,
                    new ArmorInfoPacket(
                            newEnergy,
                            maxEnergy,
                            player.getData(ModAttachments.MAX_ENERGY_REGEN),
                            player.getData(ModAttachments.SUIT_MODE)
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
        if (dx*dx + dz*dz > 0.001) return CLOAK_DRAIN_MOVING;
        return CLOAK_DRAIN_STILL;
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        LivingEntity e = event.getEntity();
        if (e instanceof Player p && !p.level().isClientSide()) {
            event.setAmount(SuitUtils.absorbDamage(p, event.getAmount()));
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity e = event.getEntity();
        if (e instanceof Player p && !p.level().isClientSide()) {
            if (SuitUtils.absorbFallDamage(p, event.getDistance() * event.getDamageMultiplier())) {
                event.setCanceled(true);
            }
        }
    }
}
