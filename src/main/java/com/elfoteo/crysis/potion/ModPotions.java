package com.elfoteo.crysis.potion;
import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.effect.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
public class ModPotions {
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(BuiltInRegistries.POTION,
            CrysisMod.MOD_ID);
    public static final Holder<Potion> SLIMEY_POTION = POTIONS.register("slimey_potion",
            () -> new Potion(new MobEffectInstance(ModEffects.SLIMEY_EFFECT, 1200, 0)));
    public static void register(IEventBus eventBus) {
        POTIONS.register(eventBus);
    }
}
