package com.elfoteo.crysis.attachments;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillState;
import com.mojang.serialization.Codec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CrysisMod.MOD_ID);

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
            () -> AttachmentType.builder(() -> 1)
                    .serialize(Codec.INT)
                    .build()
    );

    public static final Codec<Map<Skill, SkillState>> SKILLSTATE_MAP_CODEC =
            Codec.unboundedMap(SkillState.SKILL_CODEC, SkillState.CODEC);

    public static final Supplier<AttachmentType<Map<Skill,SkillState>>> ALL_SKILLS =
            ATTACHMENT_TYPES.register("skill_state_map",
                    () -> AttachmentType.<Map<Skill,SkillState>>builder(() -> {
                        Map<Skill,SkillState> defaults = new HashMap<>();
                        for (Skill s : Skill.values()) {
                            defaults.put(s, new SkillState(s, false));
                        }
                        return defaults;
                    }).serialize(SKILLSTATE_MAP_CODEC).build()
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
