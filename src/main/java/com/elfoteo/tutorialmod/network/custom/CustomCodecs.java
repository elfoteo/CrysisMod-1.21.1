package com.elfoteo.tutorialmod.network.custom;

import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.skill.SkillState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class CustomCodecs {

    public static <E extends Enum<E>> StreamCodec<ByteBuf, E> byName(Class<E> enumClass) {
        return StreamCodec.of(
                (buf, value) -> ByteBufCodecs.STRING_UTF8.encode(buf, value.name()),
                buf -> {
                    String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                    try {
                        return Enum.valueOf(enumClass, name);
                    } catch (IllegalArgumentException e) {
                        throw new RuntimeException("Unknown enum value: " + name, e);
                    }
                }
        );
    }

    public static final StreamCodec<ByteBuf, Skill> SKILL_STREAM_CODEC = byName(Skill.class);

    public static final StreamCodec<ByteBuf, SkillState> SKILL_STATE_STREAM_CODEC = StreamCodec.of(
            (buf, state) -> {
                SKILL_STREAM_CODEC.encode(buf, state.getSkill());
                ByteBufCodecs.BOOL.encode(buf, state.isUnlocked());
            },
            buf -> {
                Skill skill = SKILL_STREAM_CODEC.decode(buf);
                boolean unlocked = ByteBufCodecs.BOOL.decode(buf);
                return new SkillState(skill, unlocked);
            }
    );
}
