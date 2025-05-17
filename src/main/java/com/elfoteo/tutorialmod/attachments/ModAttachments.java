package com.elfoteo.tutorialmod.attachments;

import com.mojang.serialization.Codec;
import com.elfoteo.tutorialmod.TutorialMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

public class ModAttachments {
    // Create the DeferredRegister for attachment types
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister
            .create(NeoForgeRegistries.ATTACHMENT_TYPES, TutorialMod.MOD_ID);
    private static final int INITAL_MAX_ENERGY = 100;
    // Existing attachment types
    public static final Supplier<AttachmentType<Float>> ENERGY = ATTACHMENT_TYPES.register(
            "energy", () -> AttachmentType.builder(() -> (float) INITAL_MAX_ENERGY).serialize(Codec.FLOAT).build());
    // Energy regeneration in energy units per second
    public static final Supplier<AttachmentType<Float>> MAX_ENERGY_REGEN = ATTACHMENT_TYPES.register(
            "max_energy_regen", () -> AttachmentType.builder(() -> 10f).serialize(Codec.FLOAT).build());
    public static final Supplier<AttachmentType<Integer>> MAX_ENERGY = ATTACHMENT_TYPES.register(
            "max_energy", () -> AttachmentType.builder(() -> INITAL_MAX_ENERGY).serialize(Codec.INT).build());
    public static final Supplier<AttachmentType<Integer>> SUIT_MODE = ATTACHMENT_TYPES.register(
            "suit_mode", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
