package com.elfoteo.crysis.commands;

import com.elfoteo.crysis.item.ModItems;
import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.network.custom.ArmorInfoPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;

/**
 * Registers the /nanosuit command, which allows administrators (perm level ≥2) to:
 *   • /nanosuit equip [<player>]
 *       Equips the full Nanosuit armor set (helmet, chestplate, leggings, boots)
 *       onto the given player (or the executor if no player is specified).
 *
 *   • /nanosuit stat get <attachment> [<player>]
 *       Retrieves the current value of the specified attachment for either the executor
 *       or the specified target player.
 *
 *   • /nanosuit stat set <attachment> <value> [<player>]
 *       Sets the specified attachment to the given value for either the executor
 *       or the specified target player.
 *
 * The four supported attachments (with their data‐types) are:
 *   • energy            (float)
 *   • max_energy_regen  (float)
 *   • max_energy        (int)
 *   • suit_mode         (int)
 */
public class NanosuitCommand {

    private static final List<String> FLOAT_ATTACHMENTS = Arrays.asList("energy", "max_energy_regen");
    private static final List<String> INT_ATTACHMENTS   = Arrays.asList("max_energy", "suit_mode");
    private static final List<String> ALL_ATTACHMENTS   = Arrays.asList(
            "energy", "max_energy_regen", "max_energy", "suit_mode"
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> nanosuitCommand = Commands.literal("nanosuit")
                .requires(source -> source.hasPermission(2))

                // /nanosuit equip [<player>]
                .then(Commands.literal("equip")
                        .executes(ctx -> {
                            ServerPlayer executor = getExecutorPlayer(ctx.getSource());
                            equipFullSuit(executor);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Equipped nanosuit on yourself."),
                                    true
                            );
                            return 1;
                        })
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                    equipFullSuit(target);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Equipped nanosuit on " + target.getName().getString() + "."),
                                            true
                                    );
                                    return 1;
                                })
                        )
                )

                // /nanosuit stat get <attachment> [<player>]
                .then(Commands.literal("stat")
                        .then(Commands.literal("get")
                                .then(Commands.argument("attachment", StringArgumentType.word())
                                        .suggests((ctx, builder) ->
                                                net.minecraft.commands.SharedSuggestionProvider.suggest(ALL_ATTACHMENTS, builder))
                                        // Without target → use executor
                                        .executes(ctx -> {
                                            String key = StringArgumentType.getString(ctx, "attachment").toLowerCase();
                                            ServerPlayer executor = getExecutorPlayer(ctx.getSource());
                                            sendAttachmentValue(ctx.getSource(), executor, key);
                                            return 1;
                                        })
                                        // With optional target player
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(ctx -> {
                                                    String key = StringArgumentType.getString(ctx, "attachment").toLowerCase();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                    sendAttachmentValue(ctx.getSource(), target, key);
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // /nanosuit stat set <attachment> <value> [<player>]
                        .then(Commands.literal("set")
                                .then(Commands.argument("attachment", StringArgumentType.word())
                                        .suggests((ctx, builder) ->
                                                net.minecraft.commands.SharedSuggestionProvider.suggest(ALL_ATTACHMENTS, builder))
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                // Without target → use executor
                                                .executes(ctx -> {
                                                    String key = StringArgumentType.getString(ctx, "attachment").toLowerCase();
                                                    double rawValue = DoubleArgumentType.getDouble(ctx, "value");
                                                    ServerPlayer executor = getExecutorPlayer(ctx.getSource());
                                                    applyAttachmentValue(ctx.getSource(), executor, key, rawValue);
                                                    return 1;
                                                })
                                                // With optional target player
                                                .then(Commands.argument("target", EntityArgument.player())
                                                        .executes(ctx -> {
                                                            String key = StringArgumentType.getString(ctx, "attachment").toLowerCase();
                                                            double rawValue = DoubleArgumentType.getDouble(ctx, "value");
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                            applyAttachmentValue(ctx.getSource(), target, key, rawValue);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                );

        dispatcher.register(nanosuitCommand);
    }

    /**
     * Equips the full Nanosuit armor set onto the given player.
     */
    private static void equipFullSuit(ServerPlayer player) {
        ItemStack helmet     = new ItemStack(ModItems.NANOSUIT_HELMET.get());
        ItemStack chestplate = new ItemStack(ModItems.NANOSUIT_CHESTPLATE.get());
        ItemStack leggings   = new ItemStack(ModItems.NANOSUIT_LEGGINGS.get());
        ItemStack boots      = new ItemStack(ModItems.NANOSUIT_BOOTS.get());

        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.setItemSlot(EquipmentSlot.CHEST, chestplate);
        player.setItemSlot(EquipmentSlot.LEGS, leggings);
        player.setItemSlot(EquipmentSlot.FEET, boots);
    }

    /**
     * Retrieves and sends the current value of the specified attachment for `player`.
     */
    private static void sendAttachmentValue(CommandSourceStack source, ServerPlayer player, String key) throws CommandSyntaxException {
        switch (key) {
            case "energy": {
                float value = player.getData(ModAttachments.ENERGY.get());
                source.sendSuccess(() -> Component.literal(
                                player.getName().getString() + " current energy: " + value),
                        false
                );
                break;
            }
            case "max_energy_regen": {
                float value = player.getData(ModAttachments.MAX_ENERGY_REGEN.get());
                source.sendSuccess(() -> Component.literal(
                                player.getName().getString() + " current max_energy_regen: " + value),
                        false
                );
                break;
            }
            case "max_energy": {
                int value = player.getData(ModAttachments.MAX_ENERGY.get());
                source.sendSuccess(() -> Component.literal(
                                player.getName().getString() + " current max_energy: " + value),
                        false
                );
                break;
            }
            case "suit_mode": {
                int value = player.getData(ModAttachments.SUIT_MODE.get());
                source.sendSuccess(() -> Component.literal(
                                player.getName().getString() + " current suit_mode: " + value),
                        false
                );
                break;
            }
            default:
                throw new SimpleCommandExceptionType(
                        Component.literal("Attachment must be one of: " + ALL_ATTACHMENTS)
                ).create();
        }
    }

    /**
     * Sets the specified attachment on `player` to `rawValue`.
     */
    private static void applyAttachmentValue(CommandSourceStack source, ServerPlayer player, String key, double rawValue) throws CommandSyntaxException {
        switch (key) {
            case "energy": {
                float fval = (float) rawValue;
                player.setData(ModAttachments.ENERGY.get(), fval);
                source.sendSuccess(() -> Component.literal(
                                "Set " + player.getName().getString() + " energy to " + fval),
                        false
                );
                break;
            }
            case "max_energy_regen": {
                float fval = (float) rawValue;
                player.setData(ModAttachments.MAX_ENERGY_REGEN.get(), fval);
                source.sendSuccess(() -> Component.literal(
                                "Set " + player.getName().getString() + " max_energy_regen to " + fval),
                        false
                );
                break;
            }
            case "max_energy": {
                int ival = (int) rawValue;
                player.setData(ModAttachments.MAX_ENERGY.get(), ival);
                source.sendSuccess(() -> Component.literal(
                                "Set " + player.getName().getString() + " max_energy to " + ival),
                        false
                );
                break;
            }
            case "suit_mode": {
                int ival = (int) rawValue;
                player.setData(ModAttachments.SUIT_MODE.get(), ival);
                source.sendSuccess(() -> Component.literal(
                                "Set " + player.getName().getString() + " suit_mode to " + ival),
                        false
                );
                break;
            }
            default:
                throw new SimpleCommandExceptionType(
                        Component.literal("Attachment must be one of: " + ALL_ATTACHMENTS)
                ).create();
        }
        syncNanosuitData(player);
    }

    /**
     * Sends an updated ArmorInfoPacket to the given player to sync nanosuit data.
     */
    private static void syncNanosuitData(ServerPlayer player) {
        ArmorInfoPacket newData = new ArmorInfoPacket(
                player.getData(ModAttachments.ENERGY.get()),
                player.getData(ModAttachments.MAX_ENERGY.get()),
                player.getData(ModAttachments.MAX_ENERGY_REGEN.get()),
                player.getData(ModAttachments.SUIT_MODE.get())
        );
        PacketDistributor.sendToPlayer(player, newData);
    }

    /**
     * Helper: If the source is a player, return that player; otherwise throw CommandSyntaxException.
     */
    private static ServerPlayer getExecutorPlayer(CommandSourceStack source) throws CommandSyntaxException {
        try {
            return source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            throw new SimpleCommandExceptionType(
                    Component.literal("This command can only be run by a player.")
            ).create();
        }
    }
}
