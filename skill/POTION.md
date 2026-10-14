# 添加药水

## 一、注册 Potion

`register/PotionRegister.java`：

```java
@EventBusSubscriber
public class PotionRegister {

    static final DeferredRegister<Potion> POTION =
            DeferredRegister.create(Registries.POTION, ThriveMod.MOD_ID);

    public static final DeferredHolder<Potion, Potion> SUPER_LONG_FIRE_RESISTANCE;

    static {
        SUPER_LONG_FIRE_RESISTANCE = POTION.register("super_long_fire_resistance",
                () -> new Potion(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 48000)));
    }

    public static void register(IEventBus bus) {
        POTION.register(bus);
    }
}
```

`MobEffectInstance(effect, duration, amplifier)`：amplifier 0 = I 级、1 = II 级、2 = III 级。

主类调 `PotionRegister.register(bus)`。

### name 参数

`new Potion(String name, MobEffectInstance... effects)` 第一参是翻译键尾缀。

```java
new Potion(new MobEffectInstance(...))               // name = null
new Potion("fire_resistance", new MobEffectInstance(...))   // name = "fire_resistance"
```

| name | 翻译键 |
|---|---|
| `null` | `item.minecraft.potion.effect.<注册路径>` |
| `"fire_resistance"` | `item.minecraft.potion.effect.fire_resistance` |

传原版已有的名字 → **复用原版翻译键，语言文件一行不写**。

传原版没有的名字 → **定义自己的通用翻译键**。

传 `null` → 独立翻译键，语言文件自己写。

## 二、酿造配方

`RegisterBrewingRecipesEvent`，1.21.1 不支持 `minecraft:brewing` 数据包。

```java
@SubscribeEvent
public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
    event.getBuilder().addMix(
            Potions.LONG_FIRE_RESISTANCE,
            ItemRegister.ALLIUM_HONEY.get(),
            PotionRegister.SUPER_LONG_FIRE_RESISTANCE
    );
}
```

`Builder` 三个方法：

| 方法 | 用途 |
|---|---|
| `addMix(Holder<Potion> in, Item reagent, Holder<Potion> out)` | 药水 + 物品 → 药水 |
| `addStartMix(Item reagent, Holder<Potion> out)` | 水 → 平凡 → 药水 |
| `addRecipe(Ingredient, Ingredient, ItemStack)` | 自定义逻辑 |

`addMix` 自动处理水/喷溅/滞留三容器，不用构造 `ItemStack`，不用碰 `PotionContents`。

## 三、语言文件

键不含命名空间。

**name 传原版名** → 零语言文件。

**name 传 null** → 四种形态各写一条：

```json
"item.minecraft.potion.effect.super_long_fire_resistance": "超长效抗火药水",
"item.minecraft.splash_potion.effect.super_long_fire_resistance": "喷溅型超长效抗火药水",
"item.minecraft.lingering_potion.effect.super_long_fire_resistance": "滞留型超长效抗火药水",
"item.minecraft.tipped_arrow.effect.super_long_fire_resistance": "超长效抗火之箭"
```

## 四、清单

| 文件 | 改动 |
|---|---|
| `register/PotionRegister.java` | 注册 + `addMix` |
| `register/ItemRegister.java` | 对应蜂蜜物品 |
| `ThriveMod.java` | `PotionRegister.register(bus)` |
| `lang/{en_us,zh_cn}.json` | 仅 `name = null` 时写 |

## 五、常见坑

| 坑 | 症状 |
|---|---|
| 键名带 `thrive.` | 显示原始 key |
| 四种形态键写不全 | 对应形态显示原始 key |
| 数据包写 `minecraft:brewing` | `Unknown registry key ... recipe_serializer` |
| `addMix` 传 `ItemStack` | 编译错，需 `Holder<Potion>` |
| 忘 `register(bus)` | `Registry is already frozen` |