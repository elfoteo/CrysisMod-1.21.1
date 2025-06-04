package com.elfoteo.crysis.network.custom;

import com.elfoteo.crysis.flag.FlagInfo;
import com.elfoteo.crysis.flag.Team;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    public static final StreamCodec<ByteBuf, Map<Skill, SkillState>> SKILL_STATE_MAP_STREAM_CODEC = StreamCodec.of(
            (buf, map) -> {
                ByteBufCodecs.VAR_INT.encode(buf, map.size());
                for (Map.Entry<Skill, SkillState> entry : map.entrySet()) {
                    SKILL_STREAM_CODEC.encode(buf, entry.getKey());
                    SKILL_STATE_STREAM_CODEC.encode(buf, entry.getValue());
                }
            },
            buf -> {
                int size = ByteBufCodecs.VAR_INT.decode(buf);
                Map<Skill, SkillState> result = new EnumMap<>(Skill.class);
                for (int i = 0; i < size; i++) {
                    Skill skill = SKILL_STREAM_CODEC.decode(buf);
                    SkillState state = SKILL_STATE_STREAM_CODEC.decode(buf);
                    result.put(skill, state);
                }
                return result;
            }
    );

    public static final StreamCodec<ByteBuf, Team> TEAM_STREAM_CODEC = byName(Team.class);

    public static final StreamCodec<ByteBuf, FlagInfo> FLAG_INFO_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,            // X coordinate
            flagInfo -> flagInfo.getPos().getX(),
            ByteBufCodecs.INT,            // Y coordinate
            flagInfo -> flagInfo.getPos().getY(),
            ByteBufCodecs.INT,            // Z coordinate
            flagInfo -> flagInfo.getPos().getZ(),
            ByteBufCodecs.STRING_UTF8,    // Display name
            FlagInfo::getName,
            TEAM_STREAM_CODEC,            // Owner team
            FlagInfo::getOwner,
            (x, y, z, name, team) -> new FlagInfo(new BlockPos(x, y, z), name, team)
    );

    public static final StreamCodec<ByteBuf, List<FlagInfo>> FLAG_INFO_LIST_CODEC = StreamCodec.of(
            // Encoder: write size, then each FlagInfo
            (buf, list) -> {
                ByteBufCodecs.VAR_INT.encode(buf, list.size());
                for (FlagInfo info : list) {
                    FLAG_INFO_CODEC.encode(buf, info);
                }
            },
            // Decoder: read size, then decode each FlagInfo
            buf -> {
                int size = ByteBufCodecs.VAR_INT.decode(buf);
                List<FlagInfo> result = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    result.add(FLAG_INFO_CODEC.decode(buf));
                }
                return result;
            }
    );
}
