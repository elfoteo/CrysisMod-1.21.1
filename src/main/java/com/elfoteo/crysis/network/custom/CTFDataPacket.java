package com.elfoteo.crysis.network.custom;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.flag.FlagInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record CTFDataPacket(List<FlagInfo> flags, int redScore, int blueScore, boolean ctfEnabled) implements CustomPacketPayload {
    public static final Type<CTFDataPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "flag_info")
    );

    public static final StreamCodec<ByteBuf, CTFDataPacket> STREAM_CODEC = StreamCodec.composite(
            CustomCodecs.FLAG_INFO_LIST_CODEC,
            CTFDataPacket::flags,
            ByteBufCodecs.VAR_INT,
            CTFDataPacket::blueScore,
            ByteBufCodecs.VAR_INT,
            CTFDataPacket::blueScore,
            ByteBufCodecs.BOOL,
            CTFDataPacket::ctfEnabled,
            CTFDataPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
