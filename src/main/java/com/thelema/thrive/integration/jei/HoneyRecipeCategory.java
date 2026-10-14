package com.thelema.thrive.integration.jei;

import com.thelema.thrive.generic.blockentity.other.GardenHiveRecipes;
import com.thelema.thrive.register.ItemRegister;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class HoneyRecipeCategory implements IRecipeCategory<GardenHiveRecipes.Recipe> {

    public static final RecipeType<GardenHiveRecipes.Recipe> TYPE =
            RecipeType.create("thrive", "garden_hive", GardenHiveRecipes.Recipe.class);

    private final IDrawable icon;
    private final IDrawable arrow;

    public HoneyRecipeCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(new ItemStack(Items.HONEY_BOTTLE));
        this.arrow = helper.getRecipeArrow();
    }

    @Override public RecipeType<GardenHiveRecipes.Recipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.thrive.garden_hive"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 130; }
    @Override public int getHeight() { return 40; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, GardenHiveRecipes.Recipe r, IFocusGroup focuses) {
        builder.addInputSlot(5, 10).addItemStack(new ItemStack(r.a()));
        builder.addInputSlot(25, 10).addItemStack(new ItemStack(r.b()));
        builder.addInputSlot(45, 10).addItemStack(new ItemStack(r.c()));
        builder.addOutputSlot(100, 10).addItemStack(new ItemStack(r.honey()));
    }

    @Override
    public void draw(GardenHiveRecipes.Recipe r, IRecipeSlotsView slots,
                     GuiGraphics g, double mouseX, double mouseY) {
        arrow.draw(g, 70, 12);
    }
}