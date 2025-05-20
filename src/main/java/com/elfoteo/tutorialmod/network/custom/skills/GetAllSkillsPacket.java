package com.elfoteo.tutorialmod.network.custom.skills;

import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.network.custom.CustomCodecs;
import com.elfoteo.tutorialmod.skill.SkillState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record GetAllSkillsPacket(List<SkillState> allSkills) implements CustomPacketPayload {
    public static final Type<GetAllSkillsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "get_unlocked_skills")
    );

    public static final StreamCodec<ByteBuf, GetAllSkillsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, SkillState>list().apply(CustomCodecs.SKILL_STATE_STREAM_CODEC),
            GetAllSkillsPacket::allSkills,
            GetAllSkillsPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
