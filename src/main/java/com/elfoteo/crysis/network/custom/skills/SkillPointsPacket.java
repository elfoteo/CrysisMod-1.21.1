package com.elfoteo.crysis.network.custom.skills;

import com.elfoteo.crysis.CrysisMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record SkillPointsPacket(int availablePoints, int maxPoints) implements CustomPacketPayload {
    public static final Type<SkillPointsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "skillpoints")
    );

    public static final StreamCodec<ByteBuf, SkillPointsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SkillPointsPacket::availablePoints,
            ByteBufCodecs.VAR_INT,
            SkillPointsPacket::maxPoints,
            SkillPointsPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
