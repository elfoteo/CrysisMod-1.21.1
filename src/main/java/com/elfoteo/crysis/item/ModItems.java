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
    public static final DeferredItem<Item> BISMUTH = ITEMS.register("bismuth",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> RAW_BISMUTH = ITEMS.register("raw_bismuth",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> CHISEL = ITEMS.register("chisel",
            () -> new ChiselItem(new Item.Properties().durability(32)));
    public static final DeferredItem<Item> RADISH = ITEMS.register("radish",
            () -> new Item(new Item.Properties().food(ModFoodProperties.RADISH)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
                        TooltipFlag tooltipFlag) {
                    tooltipComponents.add(Component.translatable("tooltip.crysis.radish.tooltip"));
                    super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                }
            });
    public static final DeferredItem<Item> FROSTFIRE_ICE = ITEMS.register("frostfire_ice",
            () -> new FuelItem(new Item.Properties(), 800));
    public static final DeferredItem<Item> STARLIGHT_ASHES = ITEMS.register("starlight_ashes",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<SwordItem> BISMUTH_SWORD = ITEMS.register("bismuth_sword",
            () -> new SwordItem(ModToolTiers.BISMUTH, new Item.Properties()
                    .attributes(SwordItem.createAttributes(ModToolTiers.BISMUTH, 5, -2.4f))));
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
    public static final DeferredItem<Item> BISMUTH_HORSE_ARMOR = ITEMS.register("bismuth_horse_armor",
            () -> new AnimalArmorItem(ModArmorMaterials.NANOSUIT_COMPOSITE, AnimalArmorItem.BodyType.EQUESTRIAN,
                    false, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> KAUPEN_SMITHING_TEMPLATE = ITEMS.register(
            "kaupen_armor_trim_smithing_template",
            () -> SmithingTemplateItem
                    .createArmorTrimTemplate(ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "kaupen")));
    public static final DeferredItem<Item> KAUPEN_BOW = ITEMS.register("kaupen_bow",
            () -> new BowItem(new Item.Properties().durability(500)));
    public static final DeferredItem<Item> BAR_BRAWL_MUSIC_DISC = ITEMS.register("bar_brawl_music_disc",
            () -> new Item(new Item.Properties().jukeboxPlayable(ModSounds.BAR_BRAWL_KEY).stacksTo(1)));
    public static final DeferredItem<Item> RADISH_SEEDS = ITEMS.register("radish_seeds",
            () -> new ItemNameBlockItem(ModBlocks.RADISH_CROP.get(), new Item.Properties()));
    public static final DeferredItem<Item> GOJI_BERRIES = ITEMS.register("goji_berries",
            () -> new ItemNameBlockItem(ModBlocks.GOJI_BERRY_BUSH.get(),
                    new Item.Properties().food(ModFoodProperties.GOJI_BERRY)));
    public static final DeferredItem<Item> GECKO_SPAWN_EGG = ITEMS.register("gecko_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GECKO, 0x31afaf, 0xffac00,
                    new Item.Properties()));
    public static final DeferredItem<Item> TOMAHAWK = ITEMS.register("tomahawk",
            () -> new TomahawkItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> RADIATION_STAFF = ITEMS.register("radiation_staff",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
