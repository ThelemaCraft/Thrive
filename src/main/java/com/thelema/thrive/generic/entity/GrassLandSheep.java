package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.entity.goal.DevourGrassGoal;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

public class GrassLandSheep extends Sheep {

    public GrassLandSheep(EntityType<? extends Sheep> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(-1, new DevourGrassGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Sheep.createAttributes()
                .add(Attributes.SCALE, 1.0);
    }

    public double getGrowthRatio() {
        var healthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) return 0.0;
        return Math.max(0.0, Math.min(1.0,
                (healthAttr.getBaseValue() - 8.0) / 42.0));
    }

    public void updateGrowthScale() {
        var scaleAttr = this.getAttribute(Attributes.SCALE);
        if (scaleAttr == null) return;
        scaleAttr.setBaseValue(1.0 + getGrowthRatio());
    }

    /** 关掉原版战利品表，掉落全走代码 */
    @Override
    public ResourceKey<LootTable> getDefaultLootTable() {
        return BuiltInLootTables.EMPTY;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);

        float mult = 1.0f + (float) (getGrowthRatio() * 9.0);

        // 羊毛：未剪毛时掉落，1 × 倍率
        if (!isSheared()) {
            int woolCount = Math.max(1, (int) Math.ceil(mult));
            spawnAtLocation(new ItemStack(woolItemFor(getColor()), woolCount), 0.0f);
        }

        // 羊肉：1~2 × 倍率
        int muttonBase = 1 + this.random.nextInt(2);
        int muttonCount = Math.max(1, (int) Math.ceil(muttonBase * mult));
        spawnAtLocation(new ItemStack(Items.MUTTON, muttonCount), 0.0f);
    }

    private static Item woolItemFor(DyeColor color) {
        return switch (color) {
            case WHITE -> Items.WHITE_WOOL;
            case ORANGE -> Items.ORANGE_WOOL;
            case MAGENTA -> Items.MAGENTA_WOOL;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_WOOL;
            case YELLOW -> Items.YELLOW_WOOL;
            case LIME -> Items.LIME_WOOL;
            case PINK -> Items.PINK_WOOL;
            case GRAY -> Items.GRAY_WOOL;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_WOOL;
            case CYAN -> Items.CYAN_WOOL;
            case PURPLE -> Items.PURPLE_WOOL;
            case BLUE -> Items.BLUE_WOOL;
            case BROWN -> Items.BROWN_WOOL;
            case GREEN -> Items.GREEN_WOOL;
            case RED -> Items.RED_WOOL;
            case BLACK -> Items.BLACK_WOOL;
        };
    }
}