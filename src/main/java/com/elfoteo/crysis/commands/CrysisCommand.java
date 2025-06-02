package com.elfoteo.crysis.commands;

import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.item.ModItems;
import com.elfoteo.crysis.network.custom.ArmorInfoPacket;
import com.elfoteo.crysis.network.custom.skills.ResetSkillsPacket;
import com.elfoteo.crysis.network.custom.skills.SkillPointsPacket;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrysisCommand {

    private static final List<String> FLOAT_ATTACHMENTS = Arrays.asList("energy", "max_energy_regen");
    private static final List<String> INT_ATTACHMENTS   = Arrays.asList("max_energy", "suit_mode");
    private static final List<String> ALL_ATTACHMENTS   = Arrays.asList(
            "energy", "max_energy_regen", "max_energy", "suit_mode"
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // root: /crysis
        LiteralArgumentBuilder<CommandSourceStack> crysisRoot = Commands.literal("crysis")
                .requires(source -> source.hasPermission(2))

                // /crysis nanosuit ...
                .then(Commands.literal("nanosuit")
                        // /crysis nanosuit equip [<targets>]
                        .then(Commands.literal("equip")
                                // no targets → equip executor
                                .executes(ctx -> {
                                    ServerPlayer executor = getExecutorPlayer(ctx.getSource());
                                    equipFullSuit(executor);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Equipped nanosuit on yourself."), true
                                    );
                                    return 1;
                                })
                                // with targets → equip each in selector
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                            for (ServerPlayer p : players) {
                                                equipFullSuit(p);
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("Equipped nanosuit on " + p.getName().getString() + "."), false
                                                );
                                            }
                                            return players.size();
                                        })
                                )
                        )

                        // /crysis nanosuit stat ...
                        .then(Commands.literal("stat")
                                // /crysis nanosuit stat get <attachment> [<targets>]
                                .then(Commands.literal("get")
                                        .then(Commands.argument("attachment", StringArgumentType.word())
                                                .suggests((ctx, builder) ->
                                                        net.minecraft.commands.SharedSuggestionProvider.suggest(ALL_ATTACHMENTS, builder)
                                                )
                                                // no targets → executor only
                                                .executes(ctx -> {
                                                    String key = StringArgumentType.getString(ctx, "attachment").toLowerCase();
                                                    ServerPlayer executor = getExecutorPlayer(ctx.getSource());
                                                    sendAttachmentValue(ctx.getSource(), executor, key);
                                                    return 1;
                                                })
                                                // with targets → loop each
                                                .then(Commands.argument("targets", EntityArgument.players())
                                                        .executes(ctx -> {
                                                            String key = StringArgumentType.getString(ctx, "attachment").toLowerCase();
                                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                                            for (ServerPlayer p : players) {
                                                                sendAttachmentValue(ctx.getSource(), p, key);
                                                            }
                                                            return players.size();
                                                        })
                                                )
                                        )
                                )

                                // /crysis nanosuit stat set <attachment> <value> [<targets>]
                                .then(Commands.literal("set")
                                        .then(Commands.argument("attachment", StringArgumentType.word())
                                                .suggests((ctx, builder) ->
                                                        net.minecraft.commands.SharedSuggestionProvider.suggest(ALL_ATTACHMENTS, builder)
                                                )
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                        // no targets → executor only
                                                        .executes(ctx -> {
                                                            String key = StringArgumentType.getString(ctx, "attachment").toLowerCase();
                                                            double rawValue = DoubleArgumentType.getDouble(ctx, "value");
                                                            ServerPlayer executor = getExecutorPlayer(ctx.getSource());
                                                            applyAttachmentValue(ctx.getSource(), executor, key, rawValue);
                                                            return 1;
                                                        })
                                                        // with targets → loop each
                                                        .then(Commands.argument("targets", EntityArgument.players())
                                                                .executes(ctx -> {
                                                                    String key = StringArgumentType.getString(ctx, "attachment").toLowerCase();
                                                                    double rawValue = DoubleArgumentType.getDouble(ctx, "value");
                                                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                                                    for (ServerPlayer p : players) {
                                                                        applyAttachmentValue(ctx.getSource(), p, key, rawValue);
                                                                    }
                                                                    return players.size();
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )
                )

                // /crysis skillpoints ...
                .then(Commands.literal("skillpoints")

                        // /crysis skillpoints get <targets>
                        .then(Commands.literal("get")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                            for (ServerPlayer p : players) {
                                                sendSkillPoints(ctx.getSource(), p);
                                            }
                                            return players.size();
                                        })
                                )
                        )

                        // /crysis skillpoints reset <targets>
                        .then(Commands.literal("reset")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                            for (ServerPlayer p : players) {
                                                resetSkillPoints(ctx.getSource(), p);
                                            }
                                            return players.size();
                                        })
                                )
                        )

                        // /crysis skillpoints set <value> <targets>
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> {
                                                    int newValue = IntegerArgumentType.getInteger(ctx, "value");
                                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                                    for (ServerPlayer p : players) {
                                                        setSkillPoints(ctx.getSource(), p, newValue);
                                                    }
                                                    return players.size();
                                                })
                                        )
                                )
                        )
                );

        dispatcher.register(crysisRoot);
    }

    //
    // ─── N A N O S U I T   H E L P E R S ────────────────────────────────────────
    //

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

    private static void sendAttachmentValue(CommandSourceStack source, ServerPlayer player, String key) throws CommandSyntaxException {
        switch (key) {
            case "energy": {
                float value = player.getData(ModAttachments.ENERGY.get());
                source.sendSuccess(() -> Component.literal(
                        player.getName().getString() + " current energy: " + value
                ), false);
                break;
            }
            case "max_energy_regen": {
                float value = player.getData(ModAttachments.MAX_ENERGY_REGEN.get());
                source.sendSuccess(() -> Component.literal(
                        player.getName().getString() + " current max_energy_regen: " + value
                ), false);
                break;
            }
            case "max_energy": {
                int value = player.getData(ModAttachments.MAX_ENERGY.get());
                source.sendSuccess(() -> Component.literal(
                        player.getName().getString() + " current max_energy: " + value
                ), false);
                break;
            }
            case "suit_mode": {
                int value = player.getData(ModAttachments.SUIT_MODE.get());
                source.sendSuccess(() -> Component.literal(
                        player.getName().getString() + " current suit_mode: " + value
                ), false);
                break;
            }
            default:
                throw new SimpleCommandExceptionType(
                        Component.literal("Attachment must be one of: " + ALL_ATTACHMENTS)
                ).create();
        }
    }

    private static void applyAttachmentValue(CommandSourceStack source, ServerPlayer player, String key, double rawValue) throws CommandSyntaxException {
        switch (key) {
            case "energy": {
                float fval = (float) rawValue;
                player.setData(ModAttachments.ENERGY.get(), fval);
                source.sendSuccess(() -> Component.literal(
                        "Set " + player.getName().getString() + " energy to " + fval
                ), false);
                break;
            }
            case "max_energy_regen": {
                float fval = (float) rawValue;
                player.setData(ModAttachments.MAX_ENERGY_REGEN.get(), fval);
                source.sendSuccess(() -> Component.literal(
                        "Set " + player.getName().getString() + " max_energy_regen to " + fval
                ), false);
                break;
            }
            case "max_energy": {
                int ival = (int) rawValue;
                player.setData(ModAttachments.MAX_ENERGY.get(), ival);
                source.sendSuccess(() -> Component.literal(
                        "Set " + player.getName().getString() + " max_energy to " + ival
                ), false);
                break;
            }
            case "suit_mode": {
                int ival = (int) rawValue;
                player.setData(ModAttachments.SUIT_MODE.get(), ival);
                source.sendSuccess(() -> Component.literal(
                        "Set " + player.getName().getString() + " suit_mode to " + ival
                ), false);
                break;
            }
            default:
                throw new SimpleCommandExceptionType(
                        Component.literal("Attachment must be one of: " + ALL_ATTACHMENTS)
                ).create();
        }

        // Sync nanosuit data back to client
        syncNanosuitData(player);
    }

    private static void syncNanosuitData(ServerPlayer player) {
        ArmorInfoPacket newData = new ArmorInfoPacket(
                player.getData(ModAttachments.ENERGY.get()),
                player.getData(ModAttachments.MAX_ENERGY.get()),
                player.getData(ModAttachments.MAX_ENERGY_REGEN.get()),
                player.getData(ModAttachments.SUIT_MODE.get())
        );
        PacketDistributor.sendToPlayer(player, newData);
    }

    //
    // ─── S K I L L P O I N T S   H E L P E R S ──────────────────────────────────
    //

    private static void sendSkillPoints(CommandSourceStack source, ServerPlayer player) {
        int available = player.getData(ModAttachments.AVAILABLE_SKILL_POINTS.get());
        int maximum   = player.getData(ModAttachments.MAX_SKILL_POINTS.get());
        source.sendSuccess(() -> Component.literal(
                player.getName().getString() + " has " + available + " / " + maximum + " skill points."
        ), false);
    }

    private static void resetSkillPoints(CommandSourceStack source, ServerPlayer player) {
        // 1) Reset available points back to max
        int max = player.getData(ModAttachments.MAX_SKILL_POINTS.get());
        player.setData(ModAttachments.AVAILABLE_SKILL_POINTS.get(), max);

        source.sendSuccess(() -> Component.literal(
                "Reset " + player.getName().getString() + " available skill points to " + max + "."
        ), true);

        // 2) Reset ALL_SKILLS map: lock every skill again
        Map<Skill, SkillState> defaultMap = new HashMap<>();
        for (Skill s : Skill.values()) {
            defaultMap.put(s, new SkillState(s, false));
        }
        player.setData(ModAttachments.ALL_SKILLS.get(), defaultMap);

        // 3) Sync both skill‐points and skill‐states back to client
        syncSkillPoints(player);
        ResetSkillsPacket resetPacket = new ResetSkillsPacket();
        PacketDistributor.sendToPlayer(player, resetPacket);
    }

    private static void setSkillPoints(CommandSourceStack source, ServerPlayer player, int newValue) {
        int max = player.getData(ModAttachments.MAX_SKILL_POINTS.get());
        int clamped = Math.min(newValue, max);
        player.setData(ModAttachments.AVAILABLE_SKILL_POINTS.get(), clamped);
        source.sendSuccess(() -> Component.literal(
                "Set " + player.getName().getString() + " available skill points to " + clamped + "."
        ), true);

        // Sync to client as well
        syncSkillPoints(player);
    }

    private static void syncSkillPoints(ServerPlayer player) {
        int available = player.getData(ModAttachments.AVAILABLE_SKILL_POINTS.get());
        int max       = player.getData(ModAttachments.MAX_SKILL_POINTS.get());
        SkillPointsPacket packet = new SkillPointsPacket(available, max);
        PacketDistributor.sendToPlayer(player, packet);
    }

    //
    // ─── U T I L   M E T H O D ───────────────────────────────────────────────────
    //

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
