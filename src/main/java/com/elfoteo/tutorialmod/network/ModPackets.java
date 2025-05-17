package com.elfoteo.tutorialmod.network;
import com.elfoteo.tutorialmod.network.custom.*;
import com.elfoteo.tutorialmod.network.handlers.ClientPayloadHandler;
import com.elfoteo.tutorialmod.network.handlers.ServerPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
public class ModPackets {
    public static void registerClient(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1"); // Network version
        registrar.playBidirectional(
                ArmorInfoPacket.TYPE,
                ArmorInfoPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ClientPayloadHandler::handleArmorInfoPacket,
                        ServerPayloadHandler::handleArmorInfoPacket));
        registrar.playBidirectional(
                SuitModePacket.TYPE,
                SuitModePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ClientPayloadHandler::handleSuitModePacket,
                        ServerPayloadHandler::handleSuitModePacket));
    }
    public static void registerServer(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1"); // Network version
        registrar.playBidirectional(
                ArmorInfoPacket.TYPE,
                ArmorInfoPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> {
                        },
                        ServerPayloadHandler::handleArmorInfoPacket));
        registrar.playBidirectional(
                SuitModePacket.TYPE,
                SuitModePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> {
                        },
                        ServerPayloadHandler::handleSuitModePacket));
    }
}
