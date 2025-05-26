package com.elfoteo.crysis.datagen;
import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.enchantment.ModEnchantments;
import com.elfoteo.crysis.trim.ModTrimMaterials;
import com.elfoteo.crysis.trim.ModTrimPatterns;
import com.elfoteo.crysis.worldgen.ModBiomeModifiers;
import com.elfoteo.crysis.worldgen.ModConfiguredFeatures;
import com.elfoteo.crysis.worldgen.ModPlacedFeatures;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
public class ModDatapackProvider extends DatapackBuiltinEntriesProvider {
    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.TRIM_MATERIAL, ModTrimMaterials::bootstrap)
            .add(Registries.TRIM_PATTERN, ModTrimPatterns::bootstrap)
            .add(Registries.ENCHANTMENT, ModEnchantments::bootstrap)
            .add(Registries.CONFIGURED_FEATURE, ModConfiguredFeatures::bootstrap)
            .add(Registries.PLACED_FEATURE, ModPlacedFeatures::bootstrap)
            .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, ModBiomeModifiers::bootstrap);
    public ModDatapackProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(CrysisMod.MOD_ID));
    }
}
