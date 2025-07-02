package com.elfoteo.crysis.block.entity;
import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.block.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(BuiltInRegistries.BLOCK_ENTITY_TYPE, CrysisMod.MOD_ID);
    public static final Supplier<BlockEntityType<FlagBlockEntity>> FLAG_BE = BLOCK_ENTITIES
            .register("flag_be", () -> BlockEntityType.Builder.of(
                    FlagBlockEntity::new, ModBlocks.FLAG.get()).build(null));

    public static final Supplier<BlockEntityType<CreativeVendingMachineBlockEntity>> CREATIVE_VENDING_MACHINE_BE = BLOCK_ENTITIES
            .register("creative_vending_machine_be", () -> BlockEntityType.Builder.of(
                    CreativeVendingMachineBlockEntity::new, ModBlocks.CREATIVE_VENDING_MACHINE.get()).build(null));
    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
