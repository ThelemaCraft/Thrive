package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber
public class PotionRegister {

    static final DeferredRegister<Potion> POTION =
            DeferredRegister.create(Registries.POTION, ThriveMod.MOD_ID);

    // ============ 缓降 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_SLOW_FALLING =
            longP("super_long_slow_falling", "slow_falling", MobEffects.SLOW_FALLING, 4800);

    // ============ 牛奶 ============
    public static final DeferredHolder<Potion, Potion> MILK =
            POTION.register("milk", () -> new Potion("milk",
                    new MobEffectInstance(EffectRegister.MILK, 900)));
    public static final DeferredHolder<Potion, Potion> LONG_MILK =
            POTION.register("long_milk", () -> new Potion("milk",
                    new MobEffectInstance(EffectRegister.MILK, 1800)));
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_MILK =
            POTION.register("super_long_milk", () -> new Potion("milk",
                    new MobEffectInstance(EffectRegister.MILK, 7200)));

    // ============ 迅捷 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_SWIFTNESS =
            longP("super_long_swiftness", "swiftness", MobEffects.MOVEMENT_SPEED, 9600);
    public static final DeferredHolder<Potion, Potion> SUPER_STRONG_SWIFTNESS =
            strongP("super_strong_swiftness", "swiftness", MobEffects.MOVEMENT_SPEED, 1800, 1);

    // ============ 力量 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_STRENGTH =
            longP("super_long_strength", "strength", MobEffects.DAMAGE_BOOST, 9600);
    public static final DeferredHolder<Potion, Potion> SUPER_STRONG_STRENGTH =
            strongP("super_strong_strength", "strength", MobEffects.DAMAGE_BOOST, 1800, 1);


    // ============ 抗火 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_FIRE_RESISTANCE =
            longP("super_long_fire_resistance", "fire_resistance", MobEffects.FIRE_RESISTANCE, 9600);

    // ============ 跳跃 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_LEAPING =
            longP("super_long_leaping", "leaping", MobEffects.JUMP, 9600);
    public static final DeferredHolder<Potion, Potion> SUPER_STRONG_LEAPING =
            strongP("super_strong_leaping", "leaping", MobEffects.JUMP, 1800, 1);

    // ============ 恢复 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_REGENERATION =
            longP("super_long_regeneration", "regeneration", MobEffects.REGENERATION, 1800);
    public static final DeferredHolder<Potion, Potion> SUPER_STRONG_REGENERATION =
            strongP("super_strong_regeneration", "regeneration", MobEffects.REGENERATION, 450, 1);

    // ============ 隐身 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_INVISIBILITY =
            longP("super_long_invisibility", "invisibility", MobEffects.INVISIBILITY, 9600);

    // ============ 虚弱 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_WEAKNESS =
            longP("super_long_weakness", "weakness", MobEffects.WEAKNESS, 4800);

    // ============ 水肺 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_WATER_BREATHING =
            longP("super_long_water_breathing", "water_breathing", MobEffects.WATER_BREATHING, 9600);

    // ============ 剧毒 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_POISON =
            longP("super_long_poison", "poison", MobEffects.POISON, 1800);
    public static final DeferredHolder<Potion, Potion> SUPER_STRONG_POISON =
            strongP("super_strong_poison", "poison", MobEffects.POISON, 432, 1);

    // ============ 缓慢 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_SLOWNESS =
            longP("super_long_slowness", "slowness", MobEffects.MOVEMENT_SLOWDOWN, 4800);
    public static final DeferredHolder<Potion, Potion> SUPER_STRONG_SLOWNESS =
            strongP("super_strong_slowness", "slowness", MobEffects.MOVEMENT_SLOWDOWN, 400, 2);

    // ============ 夜视 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_NIGHT_VISION =
            longP("super_long_night_vision", "night_vision", MobEffects.NIGHT_VISION, 9600);

    // ============ 织网 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_WEAVING =
            longP("super_long_weaving", "weaving", MobEffects.WEAVING, 3600);

    // ============ 渗浆 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_OOZING =
            longP("super_long_oozing", "oozing", MobEffects.OOZING, 3600);

    // ============ 虫蚀 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_INFESTED =
            longP("super_long_infested", "infested", MobEffects.INFESTED, 3600);

    // ============ 蓄风 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_WIND_CHARGED =
            longP("super_long_wind_charged", "wind_charged", MobEffects.WIND_CHARGED, 3600);

    // ============ 凋零 ============
    public static final DeferredHolder<Potion, Potion> WITHER =
            POTION.register("wither", () -> new Potion("wither",
                    new MobEffectInstance(MobEffects.WITHER, 900)));
    public static final DeferredHolder<Potion, Potion> LONG_WITHER =
            POTION.register("long_wither", () -> new Potion("wither",
                    new MobEffectInstance(MobEffects.WITHER, 2400)));
    public static final DeferredHolder<Potion, Potion> STRONG_WITHER =
            POTION.register("strong_wither", () -> new Potion("wither",
                    new MobEffectInstance(MobEffects.WITHER, 450, 1)));
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_WITHER =
            longP("super_long_wither", "wither", MobEffects.WITHER, 2400);
    public static final DeferredHolder<Potion, Potion> SUPER_STRONG_WITHER =
            strongP("super_strong_wither", "wither", MobEffects.WITHER, 450, 1);

    // ============ 治疗（瞬发） ============
    public static final DeferredHolder<Potion, Potion> SUPER_STRONG_HEALING =
            strongP("super_strong_healing", "healing", MobEffects.HEAL, 1, 1);

    // ============ 伤害（瞬发） ============
    public static final DeferredHolder<Potion, Potion> SUPER_STRONG_HARMING =
            strongP("super_strong_harming", "harming", MobEffects.HARM, 1, 1);

    // ============ 神龟 ============
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_TURTLE_MASTER =
            POTION.register("super_long_turtle_master", () -> new Potion("turtle_master",
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 800 * 4, 3),
                    new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 800 * 4, 2)));
    public static final DeferredHolder<Potion, Potion> SUPER_STRONG_TURTLE_MASTER =
            POTION.register("super_strong_turtle_master", () -> new Potion("turtle_master",
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 6),
                    new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 4)));

    // ============ 漂浮 ============
    public static final DeferredHolder<Potion, Potion> LEVITATION =
            POTION.register("levitation", () -> new Potion("levitation",
                    new MobEffectInstance(MobEffects.LEVITATION, 1800)));
    public static final DeferredHolder<Potion, Potion> LONG_LEVITATION =
            POTION.register("long_levitation", () -> new Potion("levitation",
                    new MobEffectInstance(MobEffects.LEVITATION, 4800)));
    public static final DeferredHolder<Potion, Potion> SUPER_LONG_LEVITATION =
            longP("super_long_levitation", "levitation", MobEffects.LEVITATION, 4800);

    // ============ 辅助 ============

    private static DeferredHolder<Potion, Potion> longP(String name, String base,
                                                        net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                                        int srcDur) {
        return POTION.register(name, () -> new Potion(base,
                new MobEffectInstance(effect, srcDur * 4)));
    }

    private static DeferredHolder<Potion, Potion> strongP(String name, String base,
                                                          net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                                          int srcDur, int srcAmp) {
        return POTION.register(name, () -> new Potion(base,
                new MobEffectInstance(effect, srcDur, srcAmp + 1)));
    }

    public static void register(IEventBus bus) {
        POTION.register(bus);
    }

    // ============ 配方 ============

    @SubscribeEvent
    public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        var b = event.getBuilder();

        // 牛奶：红石延长 → 牡丹蜂蜜超长效
        b.addMix(MILK, Items.REDSTONE, LONG_MILK);
        b.addMix(LONG_MILK, ItemRegister.PEONY_HONEY.get(), SUPER_LONG_MILK);

        // 1 蒲公英 → 缓降
        b.addMix(Potions.LONG_SLOW_FALLING, ItemRegister.DANDELION_HONEY.get(), SUPER_LONG_SLOW_FALLING);

        // 2 虞美人 → 治疗
        b.addMix(Potions.STRONG_HEALING, ItemRegister.POPPY_HONEY.get(), SUPER_STRONG_HEALING);

        // 3 兰花 → 水肺
        b.addMix(Potions.LONG_WATER_BREATHING, ItemRegister.BLUE_ORCHID_HONEY.get(), SUPER_LONG_WATER_BREATHING);

        // 4 绒球葱 → 织网
        b.addMix(Potions.WEAVING, ItemRegister.ALLIUM_HONEY.get(), SUPER_LONG_WEAVING);

        // 5 蓝花美耳草 → 隐身
        b.addMix(Potions.LONG_INVISIBILITY, ItemRegister.AZURE_BLUET_HONEY.get(), SUPER_LONG_INVISIBILITY);

        // 6 红郁金香 → 力量
        b.addMix(Potions.LONG_STRENGTH, ItemRegister.RED_TULIP_HONEY.get(), SUPER_LONG_STRENGTH);
        b.addMix(Potions.STRONG_STRENGTH, ItemRegister.RED_TULIP_HONEY.get(), SUPER_STRONG_STRENGTH);

        // 7 橙郁金香 → 抗火
        b.addMix(Potions.LONG_FIRE_RESISTANCE, ItemRegister.ORANGE_TULIP_HONEY.get(), SUPER_LONG_FIRE_RESISTANCE);

        // 8 白郁金香 → 跳跃
        b.addMix(Potions.LONG_LEAPING, ItemRegister.WHITE_TULIP_HONEY.get(), SUPER_LONG_LEAPING);
        b.addMix(Potions.STRONG_LEAPING, ItemRegister.WHITE_TULIP_HONEY.get(), SUPER_STRONG_LEAPING);

        // 9 粉郁金香 → 恢复
        b.addMix(Potions.LONG_REGENERATION, ItemRegister.PINK_TULIP_HONEY.get(), SUPER_LONG_REGENERATION);
        b.addMix(Potions.STRONG_REGENERATION, ItemRegister.PINK_TULIP_HONEY.get(), SUPER_STRONG_REGENERATION);

        // 10 滨菊 → 虚弱
        b.addMix(Potions.LONG_WEAKNESS, ItemRegister.OXEYE_DAISY_HONEY.get(), SUPER_LONG_WEAKNESS);

        // 11 矢车菊
        //b.addMix(Potions.LONG_POISON, ItemRegister.CORNFLOWER_HONEY.get(), SUPER_LONG_POISON);
        //b.addMix(Potions.STRONG_POISON, ItemRegister.CORNFLOWER_HONEY.get(), SUPER_STRONG_POISON);

        // 12 铃兰 → 剧毒
        b.addMix(Potions.LONG_POISON, ItemRegister.LILY_OF_THE_VALLEY_HONEY.get(), SUPER_LONG_POISON);
        b.addMix(Potions.STRONG_POISON, ItemRegister.LILY_OF_THE_VALLEY_HONEY.get(), SUPER_STRONG_POISON);

        // 13 凋灵玫瑰 → 凋零
        b.addMix(Potions.AWKWARD, Items.WITHER_ROSE, WITHER);
        b.addMix(WITHER, Items.REDSTONE, LONG_WITHER);
        b.addMix(WITHER, Items.GLOWSTONE_DUST, STRONG_WITHER);
        b.addMix(LONG_WITHER,   ItemRegister.WITHER_ROSE_HONEY.get(), SUPER_LONG_WITHER);
        b.addMix(STRONG_WITHER, ItemRegister.WITHER_ROSE_HONEY.get(), SUPER_STRONG_WITHER);

        // 14 火把花 —— 空

        // 15 向日葵 —— 空

        // 16 丁香 → 蓄风
        b.addMix(Potions.WIND_CHARGED, ItemRegister.LILAC_HONEY.get(), SUPER_LONG_WIND_CHARGED);

        // 17 牡丹 —— 空

        // 18 玫瑰丛 → 伤害
        b.addMix(Potions.STRONG_HARMING, ItemRegister.ROSE_BUSH_HONEY.get(), SUPER_STRONG_HARMING);

        // 19 瓶子草 → 渗浆
        b.addMix(Potions.OOZING, ItemRegister.PITCHER_PLANT_HONEY.get(), SUPER_LONG_OOZING);

        // 20 盛开杜鹃叶 → 虫蚀
        b.addMix(Potions.INFESTED, ItemRegister.FLOWERING_AZALEA_LEAVES_HONEY.get(), SUPER_LONG_INFESTED);

        // 21 盛开杜鹃 —— 空

        // 22 红树胎生苗 → 神龟
        b.addMix(Potions.LONG_TURTLE_MASTER, ItemRegister.MANGROVE_PROPAGULE_HONEY.get(), SUPER_LONG_TURTLE_MASTER);
        b.addMix(Potions.STRONG_TURTLE_MASTER, ItemRegister.MANGROVE_PROPAGULE_HONEY.get(), SUPER_STRONG_TURTLE_MASTER);

        // 23 樱花树叶 —— 空

        // 24 粉红花瓣 → 缓慢
        b.addMix(Potions.LONG_SLOWNESS, ItemRegister.PINK_PETALS_HONEY.get(), SUPER_LONG_SLOWNESS);
        b.addMix(Potions.STRONG_SLOWNESS, ItemRegister.PINK_PETALS_HONEY.get(), SUPER_STRONG_SLOWNESS);

        // 25 紫颂花 → 漂浮
        b.addMix(Potions.AWKWARD, Items.CHORUS_FLOWER, LEVITATION);
        b.addMix(LEVITATION, Items.REDSTONE, LONG_LEVITATION);
        b.addMix(LONG_LEVITATION, ItemRegister.CHORUS_FLOWER_HONEY.get(), SUPER_LONG_LEVITATION);

        // 26 孢子花 → 夜视
        b.addMix(Potions.LONG_NIGHT_VISION, ItemRegister.SPORE_BLOSSOM_HONEY.get(), SUPER_LONG_NIGHT_VISION);
    }
}