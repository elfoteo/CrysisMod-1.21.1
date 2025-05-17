package com.elfoteo.tutorialmod.network.custom;
import io.netty.buffer.ByteBuf;
import com.elfoteo.tutorialmod.TutorialMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
public record SuitModePacket(int playerId, int suitMode) implements CustomPacketPayload {
    public static final Type<SuitModePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "suit_mode"));
    // Codec for encoding/decoding
    public static final StreamCodec<ByteBuf, SuitModePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SuitModePacket::playerId,
            ByteBufCodecs.VAR_INT,
            SuitModePacket::suitMode,
            SuitModePacket::new);
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
