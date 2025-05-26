package com.elfoteo.crysis.network.custom;
import com.elfoteo.crysis.CrysisMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
public record ArmorInfoPacket(float energy, int maxEnergy, float maxEnergyRegen, int suitMode)
        implements CustomPacketPayload {
    public static final Type<ArmorInfoPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "armor_info"));
    // Codec for encoding/decoding
    public static final StreamCodec<ByteBuf, ArmorInfoPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            ArmorInfoPacket::energy,
            ByteBufCodecs.VAR_INT,
            ArmorInfoPacket::maxEnergy,
            ByteBufCodecs.FLOAT,
            ArmorInfoPacket::maxEnergyRegen,
            ByteBufCodecs.VAR_INT,
            ArmorInfoPacket::suitMode,
            ArmorInfoPacket::new);
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
