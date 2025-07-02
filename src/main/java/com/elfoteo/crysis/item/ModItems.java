package com.elfoteo.crysis.item;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.block.ModBlocks;
import com.elfoteo.crysis.entity.ModEntities;
import com.elfoteo.crysis.item.custom.*;
import com.elfoteo.crysis.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.List;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CrysisMod.MOD_ID);
    public static final DeferredItem<ArmorItem> NANOSUIT_HELMET = ITEMS.register("nanosuit_helmet",
            () -> new NanosuitArmorItem(ModArmorMaterials.NANOSUIT_COMPOSITE, ArmorItem.Type.HELMET,
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ArmorItem> NANOSUIT_CHESTPLATE = ITEMS.register("nanosuit_chestplate",
            () -> new ArmorItem(ModArmorMaterials.NANOSUIT_COMPOSITE, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ArmorItem> NANOSUIT_LEGGINGS = ITEMS.register("nanosuit_leggings",
            () -> new ArmorItem(ModArmorMaterials.NANOSUIT_COMPOSITE, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ArmorItem> NANOSUIT_BOOTS = ITEMS.register("nanosuit_boots",
            () -> new ArmorItem(ModArmorMaterials.NANOSUIT_COMPOSITE, ArmorItem.Type.BOOTS,
                    new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
