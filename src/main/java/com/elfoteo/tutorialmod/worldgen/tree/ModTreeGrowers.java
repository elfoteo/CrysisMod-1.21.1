package com.elfoteo.tutorialmod.worldgen.tree;

import com.elfoteo.tutorialmod.worldgen.ModConfiguredFeatures;
import com.elfoteo.tutorialmod.TutorialMod;
import net.minecraft.world.level.block.grower.TreeGrower;
import java.util.Optional;

public class ModTreeGrowers {
    public static final TreeGrower BLOODWOOD = new TreeGrower(TutorialMod.MOD_ID + ":bloodwood",
            Optional.empty(), Optional.of(ModConfiguredFeatures.BLOODWOOD_KEY), Optional.empty());
}
