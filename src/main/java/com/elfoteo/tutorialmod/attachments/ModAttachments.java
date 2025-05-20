package com.elfoteo.tutorialmod.attachments;

import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.skill.SkillState;
import com.mojang.serialization.Codec;
import com.elfoteo.tutorialmod.TutorialMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TutorialMod.MOD_ID);

    private static final int INITAL_MAX_ENERGY = 100;
    public static final int INITAL_SKILL_POINTS = 15;

    public static final Supplier<AttachmentType<Float>> ENERGY = ATTACHMENT_TYPES.register(
            "energy",
            () -> AttachmentType.builder(() -> (float) INITAL_MAX_ENERGY)
                    .serialize(Codec.FLOAT)
                    .build()
    );

    public static final Supplier<AttachmentType<Float>> MAX_ENERGY_REGEN = ATTACHMENT_TYPES.register(
            "max_energy_regen",
            () -> AttachmentType.builder(() -> 10f)
                    .serialize(Codec.FLOAT)
                    .build()
    );

    public static final Supplier<AttachmentType<Integer>> MAX_ENERGY = ATTACHMENT_TYPES.register(
            "max_energy",
            () -> AttachmentType.builder(() -> INITAL_MAX_ENERGY)
                    .serialize(Codec.INT)
                    .build()
    );

    public static final Supplier<AttachmentType<Integer>> SUIT_MODE = ATTACHMENT_TYPES.register(
            "suit_mode",
            () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .build()
    );

    public static final Codec<List<SkillState>> SKILLSTATE_LIST_CODEC =
            SkillState.CODEC.listOf();

    public static final Supplier<AttachmentType<List<SkillState>>> ALL_SKILLS = ATTACHMENT_TYPES.register(
            "skill_states",
            () -> AttachmentType.<List<SkillState>>builder(() -> {
                        // By default, produce a fresh List<SkillState> with all Skills locked
                        List<SkillState> defaults = new ArrayList<>();
                        for (Skill s : Skill.values()) {
                            defaults.add(new SkillState(s, false));
                        }
                        return defaults;
                    })
                    .serialize(SKILLSTATE_LIST_CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<Integer>> AVAILABLE_SKILL_POINTS = ATTACHMENT_TYPES.register(
            "available_skill_points",
            () -> AttachmentType.builder(() -> INITAL_SKILL_POINTS)   // default: all points are available
                    .serialize(Codec.INT)
                    .build()
    );

    public static final Supplier<AttachmentType<Integer>> MAX_SKILL_POINTS = ATTACHMENT_TYPES.register(
            "max_skill_points",
            () -> AttachmentType.builder(() -> INITAL_SKILL_POINTS)   // default: max points
                    .serialize(Codec.INT)
                    .build()
    );

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
