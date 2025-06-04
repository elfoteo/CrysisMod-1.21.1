package com.elfoteo.crysis.commands;

import com.elfoteo.crysis.block.entity.FlagBlockEntity;
import com.elfoteo.crysis.flag.CTFData;
import com.elfoteo.crysis.flag.Team;
import com.elfoteo.crysis.flag.FlagInfo;
import com.elfoteo.crysis.block.ModBlocks;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CTFCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> ctfCommand = Commands.literal("ctf")
                .requires(source -> source.hasPermission(2))

                // — getScore subcommand (unchanged) —
                .then(Commands.literal("getScore")
                        .executes(context -> {
                            ServerLevel overworld = context.getSource().getServer().overworld();
                            CTFData data = CTFData.getOrCreate(overworld);
                            int redScore = data.getRedScore();
                            int blueScore = data.getBlueScore();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Red score: " + redScore + ", Blue score: " + blueScore),
                                    true
                            );
                            return 1;
                        })
                )

                // — setScore subcommand (unchanged) —
                .then(Commands.literal("setScore")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"red", "blue"}, builder))
                                .then(Commands.argument("score", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            String team = StringArgumentType.getString(context, "team");
                                            int score = IntegerArgumentType.getInteger(context, "score");
                                            ServerLevel overworld = context.getSource().getServer().overworld();
                                            CTFData data = CTFData.getOrCreate(overworld);
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

                // — reset subcommand, now enhanced to also reset owners and clear surrounding blocks —
                .then(Commands.literal("reset")
                        .executes(context -> {
                            ServerLevel overworld = context.getSource().getServer().overworld();
                            CTFData data = CTFData.getOrCreate(overworld);

                            // 1) Reset both team scores
                            data.resetScores();

                            // 2) For each flag, reset its owner to NONE and clear wool/concrete in the radius=6 disk
                            for (FlagInfo flag : data.getFlags()) {
                                BlockPos flagPos = flag.getPos();
                                // Reset owner
                                data.setFlagOwner(flagPos, Team.NONE);
                                BlockEntity be = overworld.getBlockEntity(flagPos);
                                if (be instanceof FlagBlockEntity flagBE){
                                    flagBE.reset();
                                }



                            }

                            // 3) Send feedback to command sender
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Reset both scores to 0, cleared all flag owners, and removed surrounding flag blocks."),
                                    true
                            );
                            return 1;
                        })
                )

                // — setFlagName subcommand (unchanged) —
                .then(Commands.literal("setFlagName")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerLevel level = source.getLevel();
                                    Player player = source.getPlayerOrException();
                                    String name = StringArgumentType.getString(context, "name");

                                    BlockPos origin = player.blockPosition();
                                    int range = 5;

                                    boolean foundAny = false;
                                    CTFData data = CTFData.getOrCreate(level);

                                    for (int dx = -range; dx <= range; dx++) {
                                        for (int dy = -range; dy <= range; dy++) {
                                            for (int dz = -range; dz <= range; dz++) {
                                                BlockPos pos = origin.offset(dx, dy, dz);
                                                Block block = level.getBlockState(pos).getBlock();
                                                if (block == ModBlocks.FLAG.get()) {
                                                    data.setFlagName(pos, name);
                                                    source.sendSuccess(
                                                            () -> Component.literal("Set name of flag at " + pos + " to \"" + name + "\"."),
                                                            true
                                                    );
                                                    foundAny = true;
                                                }
                                            }
                                        }
                                    }

                                    if (!foundAny) {
                                        throw new SimpleCommandExceptionType(
                                                Component.literal("No flag blocks found within 5 blocks of your position.")
                                        ).create();
                                    }

                                    return 1;
                                })
                        )
                );

        dispatcher.register(ctfCommand);
    }
}
