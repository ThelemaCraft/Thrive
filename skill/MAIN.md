# Thrive 开发规范

> 全局约定。任务手册见文末索引。

---

## 一、代码风格

- **写最少代码**：表达意图的最短路径。不写可写可不写的。
- **字段默认 public**：不为封装而封装。只有需要时才 getter/setter。
- **去掉多余检查**：能由调用方保证的，不在被调方重复。
- **删掉死代码**：没被用到的，立刻删，不留"以后可能有用"。
- **注释写 why**：讲设计思路、取舍、非显而易见处。不解释代码在干什么。
- **封装要有价值**：强功能性 + 易于理解，二者缺一不如展开写。

### 正例：forRing

三层嵌套循环，展开写 15 行：

```java
default void forRing(ServerLevel level, BiomeCoreBlockEntity be, int r,
                     BiConsumer<BlockPos, BlockState> action) {
    if (r < 0) return;
    BlockPos c = be.getBlockPos();
    int minY = be.minY(), maxY = be.maxY();
    for (int dx = -r; dx <= r; dx++)
        for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            for (int y = minY; y <= maxY; y++) {
                BlockPos p = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
                action.accept(p, level.getBlockState(p));
            }
        }
}
```

调用：`forRing(level, be, r, (p, s) -> ...)`。**强功能性 + 一眼看懂。**

### 反例：advance

包住一个 `if`：

```java
default void advance(BiomeCoreBlockEntity be, String key, int want, IntConsumer process) {
    int done = be.data.getInt(key);
    while (done < want) process.accept(++done);
    be.data.putInt(key, done);
}
```

调用：`advance(be, "biome", Math.min(ring, MAX_RING), r -> processBiome(level, be, r))`。

展开等价：

```java
if (tick % TICKS_PER_RING == 0) {
    if (ring <= MAX_RING) processBiome(level, be, ring);
}
```

**功能性等价，理解成本更低 → 展开写。**

---

## 二、包结构

```
com.thelema.thrive
├── ThriveMod.java
├── generic
│   ├── block              方块
│   │   └── other          方块辅助（SurfaceItem 等组合能力）
│   ├── blockentity        方块实体
│   │   ├── convertor      转换器实现
│   │   └── other          方块实体辅助
│   ├── item               物品
│   ├── entity             实体
│   │   └── goal           实体 AI
│   ├── effect             状态效果
│   ├── fluid              流体
│   ├── world
│   │   └── feature        地物
│   └── Ritual.java        仪式
├── register               所有注册
│   ├── BlockRegister / BlockEntityRegister / ItemRegister
│   ├── EntityRegister / EffectRegister / SoundRegister
│   ├── ParticleRegister / FluidRegister / FeatureRegister
│   └── RecipeRegister / TabRegister / ...
├── client
│   ├── render
│   │   ├── blockentity    方块实体渲染
│   │   │   └── other      渲染辅助（SurfaceRenderer 等工具类）
│   │   ├── entity / effect / item
│   ├── model/entity
│   ├── particle
│   └── register           BlockEntityRenderRegister / ...
├── mixin
├── config
└── pack
```

---

## 三、注册规范

所有注册器共用同一模板：

```java
public class XxxRegister {
    static final DeferredRegister<Xxx> XXX = DeferredRegister.create(..., ThriveMod.MOD_ID);

    public static final DeferredHolder<Xxx, XxxType> NAME;

    static {
        NAME = XXX.register("name", () -> ...);
    }

    public static void register(IEventBus bus) {
        XXX.register(bus);
    }
}
```

`BlockRegister` 额外提供 `registerWithItem`：

```java
public static <B extends Block> DeferredHolder<Block, B> registerWithItem(String name, Supplier<B> supplier) {
    DeferredHolder<Block, B> block = BLOCK.register(name, supplier);
    ITEM.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    return block;
}
```

---

## 四、继承与组合

**强制基类只能继承**（`Block`、`BlockEntity`、`Item`……），继承位留给它。

**额外能力用组合**：一组相关的状态 + tick + NBT，打包成独立类，**持有**进宿主。宿主不需要 `is-a`，只需要 `has-a`。

**无状态的共享逻辑用静态工具类**。

判据：

| 东西 | 方案 |
|---|---|
| 强制基类（不继承用不了） | `extends` |
| 一组状态 + 行为 + NBT | 组合 |
| 无状态纯逻辑 | 静态工具类 |
| 仅枚举/布尔/方向等简单状态 | `BlockState` 属性或字段 |

---

## 五、任务手册

| 任务 | 手册 |
|---|---|
| 添加物品 | `ITEM.md` |
| 添加方块（无 BE） | `BLOCK.md` |
| 添加方块 + 方块实体 | `BLOCK_ENTITY.md` |
| 添加流体 | `FLUID.md` |

新增任务型文档在此登记。