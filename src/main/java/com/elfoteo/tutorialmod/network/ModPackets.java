package com.elfoteo.tutorialmod.network;
import com.elfoteo.tutorialmod.network.custom.*;
import com.elfoteo.tutorialmod.network.custom.skills.*;
import com.elfoteo.tutorialmod.network.handlers.ClientPayloadHandler;
import com.elfoteo.tutorialmod.network.handlers.ServerPayloadHandler;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
public class ModPackets {
    private static void NoOp(CustomPacketPayload a, IPayloadContext b){}

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
        registrar.playBidirectional(
                GetAllSkillsPacket.TYPE,
                GetAllSkillsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ClientPayloadHandler::handleGetUnlockedSkillsPacket,
                        ServerPayloadHandler::handleGetAllSkillsPacket));
        registrar.playBidirectional(
                ResetSkillsPacket.TYPE,
                ResetSkillsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ClientPayloadHandler::handleGetResetSkillsPacket,
                        ServerPayloadHandler::handleGetResetSkillsPacket));
        registrar.playBidirectional(
                UnlockSkillPacket.TYPE,
                UnlockSkillPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ClientPayloadHandler::handleUnlockSkillPacket,
                        ServerPayloadHandler::handleUnlockSkillPacket));
        registrar.playBidirectional(
                SkillPointsPacket.TYPE,
                SkillPointsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ClientPayloadHandler::handleSkillPointsPacket,
                        ServerPayloadHandler::handleSkillPointsPacket));
    }
    public static void registerServer(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1"); // Network version
        registrar.playBidirectional(
                ArmorInfoPacket.TYPE,
                ArmorInfoPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ModPackets::NoOp,
                        ServerPayloadHandler::handleArmorInfoPacket));
        registrar.playBidirectional(
                SuitModePacket.TYPE,
                SuitModePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ModPackets::NoOp,
                        ServerPayloadHandler::handleSuitModePacket));
        registrar.playBidirectional(
                GetAllSkillsPacket.TYPE,
                GetAllSkillsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ModPackets::NoOp,
                        ServerPayloadHandler::handleGetAllSkillsPacket));
        registrar.playBidirectional(
                ResetSkillsPacket.TYPE,
                ResetSkillsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ModPackets::NoOp,
                        ServerPayloadHandler::handleGetResetSkillsPacket));
        registrar.playBidirectional(
                UnlockSkillPacket.TYPE,
                UnlockSkillPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ModPackets::NoOp,
                        ServerPayloadHandler::handleUnlockSkillPacket));
        registrar.playBidirectional(
                SkillPointsPacket.TYPE,
                SkillPointsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ModPackets::NoOp,
                        ServerPayloadHandler::handleSkillPointsPacket));
    }
}
