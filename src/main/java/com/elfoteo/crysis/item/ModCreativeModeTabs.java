package com.elfoteo.crysis.item;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, CrysisMod.MOD_ID);
    public static final Supplier<CreativeModeTab> BISMUTH_ITEMS_TAB = CREATIVE_MODE_TAB.register("bismuth_items_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.BISMUTH.get()))
                    .title(Component.translatable("creativetab.crysis.bismuth_items"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModItems.BISMUTH);
                        output.accept(ModItems.RAW_BISMUTH);
                        output.accept(ModItems.CHISEL);
                        output.accept(ModItems.RADISH);
                        output.accept(ModItems.FROSTFIRE_ICE);
                        output.accept(ModItems.STARLIGHT_ASHES);
                        output.accept(ModItems.BISMUTH_SWORD);
                        output.accept(ModItems.NANOSUIT_HELMET);
                        output.accept(ModItems.NANOSUIT_CHESTPLATE);
                        output.accept(ModItems.NANOSUIT_LEGGINGS);
                        output.accept(ModItems.NANOSUIT_BOOTS);
                        output.accept(ModItems.BISMUTH_HORSE_ARMOR);
                        output.accept(ModItems.KAUPEN_SMITHING_TEMPLATE);
                        output.accept(ModItems.KAUPEN_BOW);
                        output.accept(ModItems.BAR_BRAWL_MUSIC_DISC);
                        output.accept(ModItems.RADISH_SEEDS);
                        output.accept(ModItems.GOJI_BERRIES);
                        output.accept(ModItems.TOMAHAWK);
                        output.accept(ModItems.RADIATION_STAFF);
                        output.accept(ModItems.GECKO_SPAWN_EGG);
                    }).build());
    public static final Supplier<CreativeModeTab> BISMUTH_BLOCK_TAB = CREATIVE_MODE_TAB.register("bismuth_blocks_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.BISMUTH_BLOCK))
                    .withTabsBefore(ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "bismuth_items_tab"))
                    .title(Component.translatable("creativetab.crysis.bismuth_blocks"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModBlocks.BISMUTH_BLOCK);
                        output.accept(ModBlocks.BISMUTH_ORE);
                        output.accept(ModBlocks.BISMUTH_DEEPSLATE_ORE);
                        output.accept(ModBlocks.MAGIC_BLOCK);
                        output.accept(ModBlocks.BISMUTH_STAIRS);
                        output.accept(ModBlocks.BISMUTH_SLAB);
                        output.accept(ModBlocks.BISMUTH_PRESSURE_PLATE);
                        output.accept(ModBlocks.BISMUTH_BUTTON);
                        output.accept(ModBlocks.BISMUTH_FENCE);
                        output.accept(ModBlocks.BISMUTH_FENCE_GATE);
                        output.accept(ModBlocks.BISMUTH_WALL);
                        output.accept(ModBlocks.BISMUTH_DOOR);
                        output.accept(ModBlocks.BISMUTH_TRAPDOOR);
                        output.accept(ModBlocks.BISMUTH_LAMP);
                        output.accept(ModBlocks.BLOODWOOD_LOG.get());
                        output.accept(ModBlocks.BLOODWOOD_WOOD.get());
                        output.accept(ModBlocks.STRIPPED_BLOODWOOD_LOG.get());
                        output.accept(ModBlocks.STRIPPED_BLOODWOOD_WOOD.get());
                        output.accept(ModBlocks.BLOODWOOD_PLANKS.get());
                        output.accept(ModBlocks.BLOODWOOD_SAPLING.get());
                        output.accept(ModBlocks.BLOODWOOD_LEAVES.get());
                        output.accept(ModBlocks.CHAIR.get());
                        output.accept(ModBlocks.PEDESTAL.get());
                        output.accept(ModBlocks.GROWTH_CHAMBER.get());
                        output.accept(ModBlocks.CREATIVE_VENDING_MACHINE.get());
                        output.accept(ModBlocks.FLAG.get());
                    }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
