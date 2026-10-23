
# 添加粒子

## 一、三层结构

| 层 | 类/文件 | 端 |
|---|---|---|
| 类型 | `ParticleType`（注册） | 双端 |
| 行为 | `Particle` + `ParticleProvider` | 仅客户端 |
| 外观 | `particles/*.json` + 纹理 | 仅客户端 |

注册和发送是双端的，渲染是仅客户端的。

## 二、注册 ParticleType

`register/ParticleRegister.java`：

```java
public class ParticleRegister {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, ThriveMod.MOD_ID);

    /** 火焰色爆炸粒子。true = 受"粒子效果：最少"设置影响。 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLAME_EXPLOSION =
            PARTICLE_TYPES.register("flame_explosion", () -> new SimpleParticleType(true));

    public static void register(IEventBus bus) {
        PARTICLE_TYPES.register(bus);
    }
}
```

`SimpleParticleType(true)` 就够了，不需要自定义 `ParticleOptions`。`true` 表示受视频设置里的"粒子效果"滑条影响；想永远显示（不受玩家设置干扰）就用 `false`。

`ParticleRegister.register(bus)` 在 `ThriveMod` 构造里调用。

## 三、粒子类

`client/particle/FlameExplosionParticle.java`：

```java
public class FlameExplosionParticle extends TextureSheetParticle {

    /** 火焰色 0xFFA525 */
    private static final float R = 1.0F;
    private static final float G = 0.647F;
    private static final float B = 0.145F;

    private final SpriteSet spriteSet;

    public FlameExplosionParticle(ClientLevel level, double x, double y, double z,
                                  double xSpeed, double ySpeed, double zSpeed,
                                  SpriteSet spriteSet) {
        super(level, x, y, z);
        this.spriteSet = spriteSet;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.setColor(R, G, B);
        this.lifetime = 10 + this.random.nextInt(6);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.quadSize = 0.6F + this.random.nextFloat() * 0.4F;
        this.setSpriteFromAge(spriteSet);
    }

    @Override
    public void tick() {
        this.setSpriteFromAge(spriteSet);
        super.tick();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet spriteSet) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new FlameExplosionParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, spriteSet);
        }
    }
}
```

- 粒子负责切换粒子的png，改变粒子的大小，移动粒子位置。

## 四、客户端注册 Provider

`client/register/ParticleProviderRegister.java`：

```java
@EventBusSubscriber(modid = ThriveMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ParticleProviderRegister {

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                ParticleRegister.FLAME_EXPLOSION.get(),
                FlameExplosionParticle.Provider::new
        );
    }
}
```
- registerSpriteSet，将声明的粒子类型与客户端的实际粒子类绑定。
- 必须在 `value = Dist.CLIENT` 下——`ParticleProvider` 是纯客户端概念。

## 五、粒子 JSON

`assets/thrive/particles/flame_explosion.json`：

```json
{
  "textures": [
    "minecraft:explosion_0",
    "minecraft:explosion_1",
    "minecraft:explosion_2",
    "minecraft:explosion_3",
    "minecraft:explosion_4",
    "minecraft:explosion_5",
    "minecraft:explosion_6",
    "minecraft:explosion_7",
    "minecraft:explosion_8",
    "minecraft:explosion_9",
    "minecraft:explosion_10",
    "minecraft:explosion_11",
    "minecraft:explosion_12",
    "minecraft:explosion_13",
    "minecraft:explosion_14",
    "minecraft:explosion_15"
  ]
}
```

- **图片推进**：粒子文件指向来多个材质，它会因setSpriteFromAge的触发而切换。setSpriteFromAge保证在粒子的生命周期内，完整且恰好地依次播放完这些材质。

**自己的纹理**放在 `assets/thrive/textures/particle/xxx.png`，JSON 里写 `thrive:xxx`。

## 六、发送粒子

`sendParticles` 是 `ServerLevel` 的方法，只能在服务端调用，网络包自动同步到追踪该区块的玩家。

```java
sl.sendParticles(
        ParticleRegister.FLAME_EXPLOSION.get(),
        cx, cy, cz,       // 中心坐标
        20,               // 数量
        0.8, 0.8, 0.8,    // 位置随机偏移半径
        0.0               // 初速度 / 额外速度
);
```

参数：

| 参数 | 作用 |
|---|---|
| `ParticleType` | 用 `ParticleRegister.XXX.get()` |
| `cx, cy, cz` | 中心位置（double） |
| `count` | 数量。`0` = 发 1 颗并赋予初速度；`> 0` = 在偏移范围内随机分布 `count` 颗 |
| `xDist, yDist, zDist` | 位置随机偏移半径（`count > 0` 时有效） |
| `speed` | `count = 0` 时是初速度；`count > 0` 时是每颗的额外随机速度 |

两种典型用法：

- **球状散射**：`count = 300, xDist/yDist/zDist = 1.5, speed = 0.3`
- **定向喷射**：`count = 0, 速度参数 = (dx, dy, dz), speed = 1.0`

客户端自发粒子用 `level.addParticle(...)`。

## 七、参数速查

### 7.1 `TextureSheetParticle` 常用字段

| 字段 | 作用 |
|---|---|
| `lifetime` | 存活 tick 数，归零消失 |
| `gravity` | 下坠加速度，`0` 不受重力 |
| `hasPhysics` | 是否与方块碰撞 |
| `quadSize` | 粒子视觉尺寸 |
| `friction` | 速度衰减系数，默认 `0.98` |
| `alpha` | 透明度，`0~1` |
| `roll` / `oRoll` | 旋转角度 |

### 7.2 颜色

```java
this.setColor(r, g, b);   // 0~1 浮点，对纹理做 tint
```

**纹理是灰度图时**：`setColor` 直接给你想要的颜色。
**纹理本身有颜色时**：`setColor` 与纹理相乘，得到色相偏暗的版本。

想要完全自定义颜色，纹理用纯白或纯灰。

### 7.3 动画序列

`SpriteSet` + `setSpriteFromAge(spriteSet)` 按 `age / lifetime` 自动选帧。JSON 里 `textures` 数组是帧序列，**顺序即播放顺序**。原版爆炸帧共 16 张，`lifetime = 10~15` 时刚好一循环。

## 八、静态初始化顺序

`ParticleRegister.PARTICLE_TYPES.register(bus)` 在 `ThriveMod` 构造里调用，早于 `RegisterParticleProvidersEvent` 触发（后者在客户端 mod 构造完成后）。

**发送粒子的代码在任何地方都能调**，只要保证 `ServerLevel` 已加载。
