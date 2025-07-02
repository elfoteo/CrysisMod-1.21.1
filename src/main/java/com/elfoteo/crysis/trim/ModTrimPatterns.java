package com.elfoteo.crysis.trim;
import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.item.ModItems;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.armortrim.TrimPattern;
import net.neoforged.neoforge.registries.DeferredItem;
public class ModTrimPatterns {
    public static void bootstrap(BootstrapContext<TrimPattern> context) {
    }
    private static void register(BootstrapContext<TrimPattern> context, DeferredItem<Item> item,
            ResourceKey<TrimPattern> key) {
        TrimPattern trimPattern = new TrimPattern(key.location(), item.getDelegate(),
                Component.translatable(Util.makeDescriptionId("trim_pattern", key.location())), false);
        context.register(key, trimPattern);
    }
}
