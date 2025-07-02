package com.elfoteo.crysis.entity;
import com.elfoteo.crysis.CrysisMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister
            .create(BuiltInRegistries.ENTITY_TYPE, CrysisMod.MOD_ID);
    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
