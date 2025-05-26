package com.elfoteo.crysis.network.custom.skills;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.network.custom.CustomCodecs;
import com.elfoteo.crysis.skill.Skill;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record UnlockSkillPacket(Skill skill, Success success) implements CustomPacketPayload {
    public static final Type<UnlockSkillPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "unlock_skill"));

    public static final StreamCodec<ByteBuf, UnlockSkillPacket> STREAM_CODEC = StreamCodec.composite(
            CustomCodecs.SKILL_STREAM_CODEC,
            UnlockSkillPacket::skill,
            CustomCodecs.byName(Success.class),
            UnlockSkillPacket::success,
            UnlockSkillPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Success {
        SUCCESS,
        FAILURE
    }
}
