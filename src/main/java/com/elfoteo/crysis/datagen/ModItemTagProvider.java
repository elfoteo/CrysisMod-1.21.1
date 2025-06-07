package com.elfoteo.crysis.datagen;
import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.block.ModBlocks;
import com.elfoteo.crysis.item.ModItems;
import com.elfoteo.crysis.util.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;
import java.util.concurrent.CompletableFuture;
public class ModItemTagProvider extends ItemTagsProvider {
    public ModItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
            CompletableFuture<TagLookup<Block>> blockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, CrysisMod.MOD_ID, existingFileHelper);
    }
    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModTags.Items.TRANSFORMABLE_ITEMS)
                .add(ModItems.BISMUTH.get())
                .add(ModItems.RAW_BISMUTH.get())
                .add(Items.COAL)
                .add(Items.STICK)
                .add(Items.COMPASS);
        tag(ItemTags.SWORDS)
                .add(ModItems.BISMUTH_SWORD.get());
        this.tag(ItemTags.TRIMMABLE_ARMOR)
                .add(ModItems.NANOSUIT_HELMET.get())
                .add(ModItems.NANOSUIT_CHESTPLATE.get())
                .add(ModItems.NANOSUIT_LEGGINGS.get())
                .add(ModItems.NANOSUIT_BOOTS.get());
        this.tag(ItemTags.TRIM_MATERIALS)
                .add(ModItems.BISMUTH.get());
        this.tag(ItemTags.TRIM_TEMPLATES)
                .add(ModItems.KAUPEN_SMITHING_TEMPLATE.get());
        this.tag(ItemTags.LOGS_THAT_BURN)
                .add(ModBlocks.BLOODWOOD_LOG.get().asItem())
                .add(ModBlocks.BLOODWOOD_WOOD.get().asItem())
                .add(ModBlocks.STRIPPED_BLOODWOOD_LOG.get().asItem())
                .add(ModBlocks.STRIPPED_BLOODWOOD_WOOD.get().asItem());
        this.tag(ItemTags.PLANKS)
                .add(ModBlocks.BLOODWOOD_PLANKS.asItem());
    }
}
