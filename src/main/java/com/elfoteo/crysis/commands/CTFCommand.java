package com.elfoteo.crysis.commands;

import com.elfoteo.crysis.flag.CaptureTheFlagData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class CTFCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> ctfCommand = Commands.literal("ctf")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("getScore")
                        .executes(context -> {
                            ServerLevel overworld = context.getSource().getServer().overworld();
                            CaptureTheFlagData data = CaptureTheFlagData.getOrCreate(overworld);
                            int redScore = data.getRedScore();
                            int blueScore = data.getBlueScore();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Red score: " + redScore + ", Blue score: " + blueScore),
                                    true
                            );
                            return 1;
                        })
                )
                .then(Commands.literal("setScore")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"red", "blue"}, builder))
                                .then(Commands.argument("score", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            String team = StringArgumentType.getString(context, "team");
                                            int score = IntegerArgumentType.getInteger(context, "score");
                                            ServerLevel overworld = context.getSource().getServer().overworld();
                                            CaptureTheFlagData data = CaptureTheFlagData.getOrCreate(overworld);
                                            if (team.equalsIgnoreCase("red")) {
                                                data.setRedScore(score);
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("Set red score to " + score),
                                                        true
                                                );
                                            } else if (team.equalsIgnoreCase("blue")) {
                                                data.setBlueScore(score);
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("Set blue score to " + score),
                                                        true
                                                );
                                            } else {
                                                throw new SimpleCommandExceptionType(
                                                        Component.literal("Invalid team. Must be red or blue.")
                                                ).create();
                                            }
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("reset")
                        .executes(context -> {
                            ServerLevel overworld = context.getSource().getServer().overworld();
                            CaptureTheFlagData data = CaptureTheFlagData.getOrCreate(overworld);
                            data.resetScores();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Reset both scores to 0"),
                                    true
                            );
                            return 1;
                        })
                );

        dispatcher.register(ctfCommand);
    }
}