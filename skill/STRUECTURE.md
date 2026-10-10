
# 添加结构

以"平原树 `biome_core_tree_plains`"为例，从头到尾走一遍。你加别的结构时，把这个名字换掉就行。

原版 `jigsaw` 结构类型，纯 JSON 实现，不写 Java。

## 一、五类文件，一次性列清

结构 `biome_core_tree_plains` 需要：

```
data/thrive/structure/biome_core_tree_plains.nbt
data/thrive/worldgen/template_pool/biome_core_tree_plains/start_pool.json
data/thrive/worldgen/structure/biome_core_tree_plains.json
data/thrive/worldgen/structure_set/biome_core_tree_plains.json
data/thrive/tags/worldgen/biome/has_structure/biome_core_tree_plains.json
```

**第一处注意**：`tags` 那一层不能少——biome 标签比 biome 定义多一个 `tags` 目录。少写会把 tag 当 biome 解析，报 `No key spawn_costs in MapLike[...]`，看不出是人话。

**第二处注意**：NBT 路径是 `structure`（单数），不是 `structures`。

## 二、NBT 文件

在游戏里用结构方块或 `/place template` 造好结构后，NBT 会落在：

```
run/saves/<世界名>/generated/minecraft/structures/biome_core_tree_plains.nbt
```

把它**复制**到：

```
src/main/resources/data/thrive/structure/biome_core_tree_plains.nbt
```

游戏运行时读的是后者，前者只是编辑器缓存的产物。

## 三、template_pool

路径：`data/thrive/worldgen/template_pool/biome_core_tree_plains/start_pool.json`

```json
{
  "name": "thrive:biome_core_tree_plains/start_pool",
  "fallback": "minecraft:empty",
  "elements": [
    {
      "weight": 1,
      "element": {
        "element_type": "minecraft:single_pool_element",
        "location": "thrive:biome_core_tree_plains",
        "processors": "minecraft:empty",
        "projection": "rigid"
      }
    }
  ]
}
```

**注意 `location`**——这是 NBT 的 namespace:path，**不含扩展名**。写成 `thrive:biome_core_tree_plains.nbt` 会报找不到。

**注意 `start_pool` 是惯例名**——jigsaw 结构必须有一个"起点池"。名字随意，只要 structure 里引用的和这里一致。

**如果你有多个变体想随机出**（比如平原上生成三种不同外观的树），在 `elements` 里加多条，各给 `weight`：

```json
"elements": [
  { "weight": 3, "element": { ... "location": "thrive:biome_core_tree_plains_a" } },
  { "weight": 1, "element": { ... "location": "thrive:biome_core_tree_plains_b" } }
]
```

权重 3:1 = 前者概率是后者 3 倍。

## 四、structure

路径：`data/thrive/worldgen/structure/biome_core_tree_plains.json`

```json
{
  "type": "minecraft:jigsaw",
  "biomes": "#thrive:has_structure/biome_core_tree_plains",
  "step": "surface_structures",
  "spawn_overrides": {},
  "terrain_adaptation": "beard_thin",
  "start_pool": "thrive:biome_core_tree_plains/start_pool",
  "size": 1,
  "start_height": { "absolute": 0 },
  "project_start_to_heightmap": "WORLD_SURFACE_WG",
  "max_distance_from_center": 80,
  "use_expansion_hack": false
}
```

**地形适配靠 `terrain_adaptation`**：

| 值 | 效果 |
|---|---|
| `beard_thin` | **推荐**。结构下方自动填一层薄土，补平小坡 |
| `beard_thick` | 填更厚，适合底座大、地面明显不平的结构 |
| `none` | 不填土，贴合原地形，坡上会悬空 |

**`step` 决定生成阶段**——`surface_structures` 在地表，`underground_structures` 在地下。放地表用前者。

**`size` 是 jigsaw 拼图段数**，单个 NBT 写 1。多段拼接才需要往上加。

## 五、structure_set（稀有度）

路径：`data/thrive/worldgen/structure_set/biome_core_tree_plains.json`

```json
{
  "structures": [
    {
      "structure": "thrive:biome_core_tree_plains",
      "weight": 1
    }
  ],
  "placement": {
    "type": "minecraft:random_spread",
    "salt": 123456789,
    "spacing": 48,
    "separation": 24,
    "spread_type": "linear"
  }
}
```

**稀有度就看 `spacing`**：

| `spacing` | 密度 |
|---|---|
| 8 | 极密 |
| 32 | 常见 |
| **48** | **推荐：稀有** |
| 96 | 极稀有（林地府邸 80） |
| 160 | 传说级 |

**`separation` 保持 `spacing / 2`**。这是两个结构中心的最小距离，防止贴脸。

**`salt` 每个结构不一样**。两个结构都用 `123456789` 会撞位置。给你个项目内的规矩：第一个用 `123456789`，第二个 `123456790`，依次递增。

**`spread_type`**：`linear` 均匀分布，`triangular` 中心密边缘疏，`linear_triangular` 混合。**开阔地形用 `linear`。**

## 六、biome 标签

路径：`data/thrive/tags/worldgen/biome/has_structure/biome_core_tree_plains.json`

```json
{
  "replace": false,
  "values": [
    "minecraft:plains",
    "minecraft:sunflower_plains"
  ]
}
```

**`replace: false` 必须有**——这是追加到原版 `#minecraft:is_overworld` 等已有标签的语义，写 `true` 会覆盖。

**只列想让结构生成的群系**。想加自己的 moss 群系就加 `thrive:moss`。

## 七、验证

进世界：

```
/locate structure thrive:biome_core_tree_plains
```

能定位到坐标就成功了。

## 八、调试：手动精准放置

### 8.1 指令

```
/place template thrive:biome_core_tree_plains 100 64 200
```

`/place template` **直接放 NBT**，不走 jigsaw，不贴地形，坐标精确。调位置、试效果用这个。

对比：`/place structure thrive:biome_core_tree_plains 100 64 200` 会走结构系统，自动 `beard_thin` 贴地形。

### 8.2 代码

```java
public static void placeTemplate(ServerLevel level, BlockPos pos) {
    StructureTemplateManager manager = level.getStructureManager();
    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("thrive", "biome_core_tree_plains");

    Optional<StructureTemplate> templateOpt = manager.get(id);
    if (templateOpt.isEmpty()) return;
    StructureTemplate template = templateOpt.get();

    StructurePlaceSettings settings = new StructurePlaceSettings()
            .setRotation(BlockRotation.NONE)
            .setMirror(BlockMirror.NONE)
            .setIgnoreEntities(false);

    template.placeInWorld(
            level,                    // 目标世界
            pos,                      // 放置起点
            pos,                      // 放置起点（用于计算偏移）
            settings,                 // 放置参数
            level.getRandom(),        // 随机源
            Block.UPDATE_ALL          // 方块更新标志
    );
}
```

`placeInWorld` 返回 `boolean`，失败返回 false，生产代码里应检查。

## 九、加第二个结构

比如加"苔藓地风格的树"，步骤：

1. **NBT**：`data/thrive/structure/biome_core_tree_moss.nbt`
2. **template_pool**：`data/thrive/worldgen/template_pool/biome_core_tree_moss/start_pool.json`——把 JSON 里所有 `biome_core_tree_plains` 换成 `biome_core_tree_moss`
3. **structure**：`data/thrive/worldgen/structure/biome_core_tree_moss.json`——同上替换
4. **structure_set**：`data/thrive/worldgen/structure_set/biome_core_tree_moss.json`——**别忘了改 `salt`**
5. **biome tag**：`data/thrive/tags/worldgen/biome/has_structure/biome_core_tree_moss.json`——替换 `values` 为 `thrive:moss` 等

**要改的四处**：5 个文件名里的后缀、4 个 JSON 里的 id 字符串、`salt`、biome 列表。**其余原样。**

加非树的结构（遗迹、小屋）也一样——把 `biome_core_tree_plains` 全换成新名字，`salt` 换成没用过的数，biome 列表按目标地形填。
