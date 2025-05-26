package com.elfoteo.crysis.network.custom.skills;

import com.elfoteo.crysis.CrysisMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ResetSkillsPacket() implements CustomPacketPayload {
    public static final Type<ResetSkillsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "reset_skills"));

    // No data to encode/decode
    public static final StreamCodec<ByteBuf, ResetSkillsPacket> STREAM_CODEC = StreamCodec.unit(new ResetSkillsPacket());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
