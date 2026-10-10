# 添加物品

## 步骤

**1. 注册字段与初始化** — `register/ItemRegister.java`

```java
public static final DeferredHolder<Item, Item> XXX;

static {
    XXX = ITEM.register("xxx", () -> new Item(new Item.Properties()));
}
```

需特殊逻辑时建类 `generic/item/XxxItem.java extends Item`：

```java
XXX = ITEM.register("xxx", XxxItem::new);
```

**2. 资源文件**

| 类型 | 路径 |
|---|---|
| 模型 | `assets/thrive/models/item/xxx.json` |
| 纹理 | `assets/thrive/textures/item/xxx.png` |
| 语言 | `assets/thrive/lang/{en_us,zh_cn}.json` |

模型：

```json
{
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "thrive:item/xxx" }
}
```

语言：

```json
"item.thrive.xxx": "Xxx"
```

## 常见属性

```java
new Item.Properties()
        .stacksTo(16)                         // 堆叠上限，默认 64
        .craftRemainder(Items.BUCKET)         // 合成后返还
        .food(new FoodProperties.Builder()
                .nutrition(4)
                .saturationModifier(0.3f)
                .build())
        .rarity(Rarity.UNCOMMON)
        .fireResistant();
```

## 注册到创造标签

`register/TabRegister.java` 里对应标签的 `displayItems` 生成器加一行：

```java
output.accept(ItemRegister.XXX.get());
```