# 添加配方

纯 JSON，不需要 Java 代码。一条配方一个文件：

```
data/thrive/recipe/<name>.json
```

`<name>` 是配方 id，任意取，通常描述用途（`lantern_berry_bucket`、`moss_block_from_smelting`）。

## 一、有序合成 Shaped

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "ABA",
    "B B",
    "ABA"
  ],
  "key": {
    "A": { "item": "minecraft:stick" },
    "B": { "item": "thrive:lantern_berry" }
  },
  "result": {
    "id": "thrive:lantern_berry_bucket",
    "count": 1
  }
}
```

**`pattern` 必须正好 3 行**，每行 1~3 字符。空格 = 空槽。字符与 `key` 对应，只能用字母（大小写敏感）。

**`key` 的值**可以是：

| 写法 | 含义 |
|---|---|
| `{ "item": "minecraft:stick" }` | 物品 |
| `{ "tag": "minecraft:planks" }` | 标签 |
| `{ "item": "minecraft:stone", "count": 2 }` | 1.21 起不推荐，key 里 count 无效 |

## 二、无序合成 Shapeless

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    { "item": "thrive:lantern_berry" },
    { "item": "minecraft:glass_bottle" }
  ],
  "result": {
    "id": "thrive:lantern_berry_juice_bucket",
    "count": 1
  }
}
```

**`ingredients` 顺序无关**。数量靠数组重复：

```json
"ingredients": [
  { "item": "minecraft:sugar" },
  { "item": "minecraft:sugar" },
  { "item": "minecraft:sugar" }
]
```

1.21 起**没有 `{ "item": "...", "count": 3 }`** —— 那种写法失效。

## 三、熔炉 Smelting

```json
{
  "type": "minecraft:smelting",
  "ingredient": { "item": "minecraft:clay_ball" },
  "result": { "id": "minecraft:brick" },
  "experience": 0.3,
  "cookingtime": 200
}
```

| 字段 | 含义 |
|---|---|
| `experience` | 0.0~1.0，产出时给的经验 |
| `cookingtime` | tick 数。原版铁 200、食物 100~600 |

**`result` 是对象**，`{ "id": "...", "count": N }`。不需要 `count` 时省略。

## 四、高炉 / 烟熏 / 营火

```json
{ "type": "minecraft:blasting", ... }
{ "type": "minecraft:smoking", ... }
{ "type": "minecraft:campfire_cooking", ... }
```

**结构完全一样**，只换 `type` 和 `cookingtime`：

| 类型 | 默认 cookingtime |
|---|---|
| `smelting` | 200 |
| `blasting` | 100 |
| `smoking` | 100 |
| `campfire_cooking` | 600 |

**高炉只吃矿石类**，烟熏只吃食物，用其他物品会**配方找不到**——不是报错，是运行时静默不匹配。

## 五、切石机 Stonecutting

```json
{
  "type": "minecraft:stonecutting",
  "ingredient": { "item": "minecraft:stone" },
  "result": { "id": "minecraft:stone_slab", "count": 2 }
}
```

**注意 `count` 写在外层**，不是嵌在 `result` 里再套一层。这是 1.21 的正确格式。

## 六、锻造升级 Smithing

1.20 之后的"下界合金式升级"：

```json
{
  "type": "minecraft:smithing_transform",
  "template": { "item": "minecraft:netherite_upgrade_smithing_template" },
  "base": { "item": "minecraft:diamond_sword" },
  "addition": { "item": "minecraft:netherite_ingot" },
  "result": { "id": "minecraft:netherite_sword" }
}
```

**四个字段缺一不可**。`template` 是锻造模板，1.20 之前没有。

## 七、盔甲纹饰 Smithing Trim

```json
{
  "type": "minecraft:smithing_trim",
  "template": { "item": "minecraft:wayfinder_armor_trim_smithing_template" },
  "base": { "tag": "minecraft:trimmable_armor" },
  "addition": { "tag": "minecraft:trim_materials" }
}
```

**没有 `result`**——纹饰不产生新物品，只是给原装备加图案。

## 八、常用 ingredient 写法

| 想匹配 | 写法 |
|---|---|
| 具体物品 | `{ "item": "thrive:lantern_berry" }` |
| 物品标签 | `{ "tag": "minecraft:planks" }` |
| 多个物品任选 | 只能靠标签，`item` 字段只接一个 id |

**物品标签**要在 `data/<ns>/tags/item/<name>.json` 里定义：

```json
{
  "replace": false,
  "values": [
    "thrive:lantern_berry",
    "minecraft:sweet_berries"
  ]
}
```

## 九、配方解锁（可选）

**不加这一段，配方在配方书里默认可见、可合成**。加 `recipe_advancement` 才需要玩家"发现"——大多数情况不需要。

**需要时**，在 `data/thrive/advancement/recipe/<name>.json` 里写解锁条件。**跳过。**

## 十、常见坑

| 坑 | 症状 |
|---|---|
| `pattern` 不满 3 行 | 加载时报错，配方不注册 |
| `pattern` 行内字符数超过 3 | 报错 |
| `key` 用了数字或符号 | 报错，只能用字母 |
| `ingredients` 里写 `{ "item": "...", "count": 3 }` | 1.21 无效，要写 3 遍 |
| `result` 写 `{ "item": "..." }` | 报错，1.21 起是 `{ "id": "..." }` |
| 高炉配方放食物 | 静默不匹配，不报错 |
| 路径写成 `recipes/` | 报错，1.21 是 `recipe/`（单数） |
| 文件名与 `result` id 不一致 | 无所谓，文件名只是 id |

## 十一、清单

| 类型 | 路径 |
|---|---|
| 所有配方 | `data/thrive/recipe/<name>.json` |
| 物品标签（可选） | `data/thrive/tags/item/<name>.json` |
| 语言（成品名） | `assets/thrive/lang/{en_us,zh_cn}.json` |

**成品物品名在语言文件里，配方 JSON 不写名字。**

## 十二、验证

进世界后：

```
/recipe give @s thrive:<name>
```

能拿到成品 = 配方生效。或直接打开配方书看有没有这一条。
