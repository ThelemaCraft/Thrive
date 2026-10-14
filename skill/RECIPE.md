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
                () -> new Potion(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 38400)));
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
new Potion(new MobEffectInstance(...))                    // name = null
new Potion("fire_resistance", new MobEffectInstance(...)) // name = "fire_resistance"
```

| name | 翻译键 |
|---|---|
| `null` | `item.minecraft.potion.effect.<注册路径>` |
| `"fire_resistance"` | `item.minecraft.potion.effect.fire_resistance` |

## 二、翻译键规则

每种药水有**四种形态**，各有独立键：

| 形态 | 键 |
|---|---|
| 普通药水 | `item.minecraft.potion.effect.<name>` |
| 喷溅药水 | `item.minecraft.splash_potion.effect.<name>` |
| 滞留药水 | `item.minecraft.lingering_potion.effect.<name>` |
| 药水箭 | `item.minecraft.tipped_arrow.effect.<name>` |

`<name>` 由构造参数决定：传 `"fire_resistance"` 则 `<name> = fire_resistance`；传 `null` 则 `<name> = 注册路径`。

### 四种键都要存在

**传 `baseName` 复用原版键**时，原版已写好的四种键直接生效，**零语言文件**。

**原版没有的药水**（如 `wither` / `levitation`），四种键原版全缺，**四条都要补**：

```json
"item.minecraft.potion.effect.wither": "凋零药水",
"item.minecraft.splash_potion.effect.wither": "喷溅型凋零药水",
"item.minecraft.lingering_potion.effect.wither": "滞留型凋零药水",
"item.minecraft.tipped_arrow.effect.wither": "凋零之箭"
```

少写哪种形态，那种形态就显示原始 key。

### 多种变体共用一条翻译

多个 `Potion` 传**同一个 `baseName`** → 共用翻译键。原版 `LONG_POISON` / `STRONG_POISON` 都传 `"poison"`，显示都为"剧毒药水"，靠颜色和时长区分。

自己的变体链（如 `wither` / `long_wither` / `strong_wither`）也全传 `"wither"`，**只写 4 条翻译**。

## 三、酿造配方

`RegisterBrewingRecipesEvent`，1.21.1 不支持 `minecraft:brewing` 数据包。

```java
@SubscribeEvent
public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
    event.getBuilder().addMix(
            Potions.LONG_FIRE_RESISTANCE,          // 输入
            ItemRegister.ALLIUM_HONEY.get(),       // 试剂
            PotionRegister.SUPER_LONG_FIRE_RESISTANCE  // 输出
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

### 每种输出只能被一种材料得到

多个 `addMix` 指向**同一输出**时，**只有第一条生效**——后注册的覆盖不了，也加不进。所以一种输出只对应一种花。

要多种花出同一药水，得注册**多个输出**（不同 `DeferredHolder`），再分别 `addMix`。

### 原版没有的前置药水

原版没有的药水（如凋零、漂浮），需要**两步链**：

```java
// 前置：粗制 + 材料
b.addMix(Potions.AWKWARD, Items.WITHER_ROSE, WITHER);
// 原版两条路径
b.addMix(WITHER, Items.REDSTONE, LONG_WITHER);
b.addMix(WITHER, Items.GLOWSTONE_DUST, STRONG_WITHER);
// 蜂蜜强化两条
b.addMix(LONG_WITHER,   ItemRegister.WITHER_ROSE_HONEY.get(), SUPER_LONG_WITHER);
b.addMix(STRONG_WITHER, ItemRegister.WITHER_ROSE_HONEY.get(), SUPER_STRONG_WITHER);
```

## 四、清单

| 文件 | 改动 |
|---|---|
| `register/PotionRegister.java` | 注册 + `addMix` |
| `register/ItemRegister.java` | 对应蜂蜜物品 |
| `ThriveMod.java` | `PotionRegister.register(bus)` |
| `lang/{en_us,zh_cn}.json` | 仅原版无对应键时写 |

## 五、常见坑

| 坑 | 症状 |
|---|---|
| 键名带 `thrive.` | 显示原始 key |
| 原版无的药水只写 1 条 | 喷溅/滞留/箭显示原始 key |
| 数据包写 `minecraft:brewing` | `Unknown registry key ... recipe_serializer` |
| `addMix` 传 `ItemStack` | 编译错，需 `Holder<Potion>` |
| 多个 `addMix` 指同一输出 | 只有第一条生效 |
| 忘 `register(bus)` | `Registry is already frozen` |