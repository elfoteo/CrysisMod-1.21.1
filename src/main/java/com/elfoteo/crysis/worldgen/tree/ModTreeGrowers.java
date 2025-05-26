package com.elfoteo.crysis.worldgen.tree;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.worldgen.ModConfiguredFeatures;
import net.minecraft.world.level.block.grower.TreeGrower;
import java.util.Optional;

public class ModTreeGrowers {
    public static final TreeGrower BLOODWOOD = new TreeGrower(CrysisMod.MOD_ID + ":bloodwood",
            Optional.empty(), Optional.of(ModConfiguredFeatures.BLOODWOOD_KEY), Optional.empty());
}
