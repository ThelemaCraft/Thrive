# Jade 联动

软依赖。Jade 不在时插件不加载、不报错、不崩溃。

## 一、依赖配置

`libs/` 里放 Jade jar，`build.gradle` 里 `compileOnly files(...)`：

```groovy
dependencies {
    compileOnly files('libs/jade-1.21.1-neoforge-15.10.6.jar')
}

repositories {
    flatDir { dirs 'libs' }
}
```

`compileOnly` = 只编译期可见，不打包进产物。

`neoforge.mods.toml` 声明可选依赖：

```toml
[[dependencies."${mod_id}"]]
modId="jade"
mandatory=false
versionRange="[15.10,)"
ordering="AFTER"
side="BOTH"
```

没有 `type="optional"` 这种字段。可选 = `mandatory=false`。`ordering="AFTER"` 保证 Jade 先加载。

## 二、三条铁律

1. 插件类不被任何非 Jade 相关类引用。Jade 不在时类加载器永远碰不到它，就不会 `NoClassDefFoundError`。
2. 不写进 `neoforge.mods.toml` 的 `mandatory=true`。一旦必装，没 Jade 的玩家直接拒绝启动。
3. `@WailaPlugin` 由 Jade 扫，不由 NeoForge 扫。Jade 不在 = 没人扫 = 那个类永远死着。

## 三、插件入口

`integration/jade/ThriveJadePlugin.java`

```java
@WailaPlugin
public class ThriveJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(GardenBeeDataProvider.INSTANCE, GardenBee.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(GardenBeeComponentProvider.INSTANCE, GardenBee.class);
    }
}
```

## 四、服务端数据提供器

服务端收集数据 → 写 `CompoundTag` → Jade 每 250ms 自动同步到客户端。

```java
public enum GardenBeeDataProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof GardenBee bee)) return;
        tag.putBoolean("Tame", bee.isTame());
        if (bee.getOwnerUUID() != null) tag.putUUID("Owner", bee.getOwnerUUID());
        tag.putBoolean("Pollen", bee.carriedPollen != null);
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath("thrive", "garden_bee_data");
    }
}
```

`appendServerData` 里只能碰服务端类。客户端专属类会 `ClassNotFoundException`。

## 五、客户端组件提供器

读 `accessor.getServerData()`，往 tooltip 加行。

```java
public enum GardenBeeComponentProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.getBoolean("Tame")) {
            tooltip.add(Component.translatable("jade.thrive.garden_bee.wild"));
            return;
        }
        if (tag.hasUUID("Owner")) {
            Player owner = accessor.getLevel().getPlayerByUUID(tag.getUUID("Owner"));
            if (owner != null)
                tooltip.add(Component.translatable("jade.thrive.garden_bee.owner",
                        owner.getName().getString()));
        }
        if (tag.getBoolean("Pollen"))
            tooltip.add(Component.translatable("jade.thrive.garden_bee.pollen"));
    }

    @Override
    public ResourceLocation getUid() {
        return GardenBeeDataProvider.INSTANCE.getUid();
    }
}
```

两个 provider 共用同一 UID，Jade 视作一个逻辑单元，配置菜单里只出现一个开关。

## 六、语言文件

每个 provider 自动生成一个配置项，键为 `config.jade.plugin_<ns>.<path>`：

```json
"config.jade.plugin_thrive.garden_bee_data": "花园蜜蜂",
"jade.thrive.garden_bee.wild": "野生",
"jade.thrive.garden_bee.owner": "主人：%s",
"jade.thrive.garden_bee.pollen": "携带花粉"
```

漏了 `config.jade.plugin_*` 的症状：Jade 配置菜单里显示原始 key。

## 七、配置选项（可选）

给用户开关：

```java
@Override
public void registerClient(IWailaClientRegistration registration) {
    ResourceLocation optionId = ResourceLocation.fromNamespaceAndPath("thrive", "show_pollen");
    registration.addConfig(optionId, true);
    registration.markAsClientFeature(optionId);
    registration.registerEntityComponent(GardenBeeComponentProvider.INSTANCE, GardenBee.class);
}
```

读取 `config.get(optionId)`。`markAsClientFeature` = 不需要服务端参与，单机也能改。

## 八、覆盖射线追踪（可选）

让 Jade 显示的和实际方块/实体不同：

```java
registration.addRayTraceCallback((hitResult, accessor, originalAccessor) -> {
    return accessor;
});
```

大多数场景不用。

## 九、常见坑

| 坑 | 症状 |
|---|---|
| `implementation` 而非 `compileOnly` | 没 Jade 时启动崩 |
| 插件类被非 Jade 类引用 | 没 Jade 时 `NoClassDefFoundError` |
| `neoforge.mods.toml` 写 `mandatory=true` | 没 Jade 拒绝启动 |
| 写 `type="optional"` | TOML 无此字段，NeoForge 忽略 |
| 两 provider 不同 UID | 配置里两个开关，用户困惑 |
| 忘写 `config.jade.plugin_*` 语言 | 配置菜单显示原始 key |
| 在 `appendServerData` 里碰客户端类 | 服务端 `ClassNotFoundException` |

## 十、清单

| 文件 | 路径 |
|---|---|
| 依赖 | `build.gradle` + `neoforge.mods.toml` |
| 插件类 | `integration/jade/ThriveJadePlugin.java` |
| 数据提供器 | `integration/jade/GardenBeeDataProvider.java` |
| 组件提供器 | `integration/jade/GardenBeeComponentProvider.java` |
| 语言 | `assets/thrive/lang/{en_us,zh_cn}.json` |
