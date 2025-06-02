package com.elfoteo.crysis.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

public class ModCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CTFCommand.register(dispatcher);
        NanosuitCommand.register(dispatcher);
    }
}
