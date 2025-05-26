package com.elfoteo.crysis.villager;
import net.neoforged.bus.api.IEventBus;

public class ModVillagers {
//    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister
//            .create(BuiltInRegistries.POINT_OF_INTEREST_TYPE, TutorialMod.MOD_ID);
//    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSIONS = DeferredRegister
//            .create(BuiltInRegistries.VILLAGER_PROFESSION, TutorialMod.MOD_ID);
//    public static final Holder<PoiType> KAUPEN_POI = POI_TYPES.register("kaupen_poi",
//            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.CHAIR.get().getStateDefinition().getPossibleStates()), 1,
//                    1));
//    public static final Holder<VillagerProfession> KAUPENGER = VILLAGER_PROFESSIONS.register("kaupenger",
//            () -> new VillagerProfession("kaupenger", holder -> holder.value() == KAUPEN_POI.value(),
//                    poiTypeHolder -> poiTypeHolder.value() == KAUPEN_POI.value(), ImmutableSet.of(), ImmutableSet.of(),
//                    ModSounds.MAGIC_BLOCK_HIT.get()));
    public static void register(IEventBus eventBus) {
        //POI_TYPES.register(eventBus);
        //VILLAGER_PROFESSIONS.register(eventBus);
    }
}
