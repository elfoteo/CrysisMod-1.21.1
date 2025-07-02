package com.elfoteo.crysis.block;
import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.block.custom.*;
import com.elfoteo.crysis.block.entity.FlagBlockEntity;
import com.elfoteo.crysis.item.ModItems;
import com.elfoteo.crysis.sound.ModSounds;
import com.elfoteo.crysis.worldgen.tree.ModTreeGrowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CrysisMod.MOD_ID);
    public static final DeferredBlock<Block> CREATIVE_VENDING_MACHINE = registerBlock("creative_vending_machine",
            () -> new CreativeVendingMachineBlock(BlockBehaviour.Properties.of().noOcclusion()));
    public static final DeferredBlock<Block> FLAG = registerBlock("flag",
            () -> new FlagBlock(BlockBehaviour.Properties.of()));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }
    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
