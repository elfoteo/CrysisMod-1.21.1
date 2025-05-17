package com.elfoteo.tutorialmod.compat;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import com.elfoteo.tutorialmod.TutorialMod;
import com.elfoteo.tutorialmod.block.ModBlocks;
import com.elfoteo.tutorialmod.recipe.GrowthChamberRecipe;
import com.elfoteo.tutorialmod.recipe.ModRecipes;
import com.elfoteo.tutorialmod.screen.custom.GrowthChamberScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import java.util.List;
@JeiPlugin
public class JEITutorialModPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "jei_plugin");
    }
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new GrowthChamberRecipeCategory(
                registration.getJeiHelpers().getGuiHelper()));
    }
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeManager recipeManager = Minecraft.getInstance().level.getRecipeManager();
        List<GrowthChamberRecipe> growthChamberRecipes = recipeManager
                .getAllRecipesFor(ModRecipes.GROWTH_CHAMBER_TYPE.get()).stream().map(RecipeHolder::value).toList();
        registration.addRecipes(GrowthChamberRecipeCategory.GROWTH_CHAMBER_RECIPE_RECIPE_TYPE, growthChamberRecipes);
    }
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(GrowthChamberScreen.class, 74, 30, 22, 20,
                GrowthChamberRecipeCategory.GROWTH_CHAMBER_RECIPE_RECIPE_TYPE);
    }
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.GROWTH_CHAMBER.asItem()),
                GrowthChamberRecipeCategory.GROWTH_CHAMBER_RECIPE_RECIPE_TYPE);
    }
}
