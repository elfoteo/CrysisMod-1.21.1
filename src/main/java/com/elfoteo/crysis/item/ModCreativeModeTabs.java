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
    public static final Supplier<CreativeModeTab> NANOSUIT_ARMOR_TAB = CREATIVE_MODE_TAB.register("nanosuit_armor_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.NANOSUIT_HELMET.get()))
                    .title(Component.translatable("creativetab.crysis.nanosuit"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModItems.NANOSUIT_HELMET);
                        output.accept(ModItems.NANOSUIT_CHESTPLATE);
                        output.accept(ModItems.NANOSUIT_LEGGINGS);
                        output.accept(ModItems.NANOSUIT_BOOTS);
                    }).build());
    public static final Supplier<CreativeModeTab> MISC_ITEMS_TAB = CREATIVE_MODE_TAB.register("misc_items_tag",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.FLAG))
                    .withTabsBefore(ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "nanosuit_armor_tab"))
                    .title(Component.translatable("creativetab.crysis.misc_items"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModBlocks.CREATIVE_VENDING_MACHINE);
                        output.accept(ModBlocks.FLAG);
                    }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
