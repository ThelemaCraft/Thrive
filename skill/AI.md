# 实体 AI 系统详解

> 依据 `neoforge-21.1.233-merged.jar` 内反编译源码逐行核对。
> 结论先行：**Goal 隔 tick 决策 -> Navigation 出路径 -> MoveControl 出参数 -> travel 才真正位移**。

---

## 0. 一句话总览

一个 Mob 的服务端 tick：

```
sensing.tick()
targetSelector.tick() / goalSelector.tick()      // 隔 tick
navigation.tick()                                // 每 tick
moveControl.tick() -> lookControl.tick() -> jumpControl.tick()   // 每 tick
travel(xxa, yya, zza)                            // 每 tick，真正改坐标
```

四层职责：

| 层 | 类 | 干什么 |
|---|---|---|
| 决策 | `Goal` / `GoalSelector` | 决定"做什么" |
| 战略 | `PathNavigation` | 算 A* 路径，逐节点给 MoveControl 设目标 |
| 战术 | `MoveControl` / `LookControl` / `JumpControl` | 把目标翻译成 `yRot`、`speed`、`zza`、`jumping` |
| 执行 | `LivingEntity.travel` | 读参数，真正 `move()` 改坐标 |

---

## 1. 核心指导：三大移动机制怎么选？

这是自定义 AI 最容易踩坑的地方。**它们底层最终都是通过 `MoveControl` 修改 `zza`（前后）和 `xxa`（左右）并配合 `yRot`（身体朝向）来产生位移的，但控制方式完全不同，绝不能混用。**

| 场景 | 用什么 | 底层行为与特征 | 适用情况 |
|---|---|---|---|
| **远距离、复杂地形** | `PathNavigation.moveTo(...)` | 算 A* 路径。**底层是在替你把路径节点传给 `MoveControl`。** 在它运行期间（`!isDone()`），**绝对不要手写代码去调 `MoveControl`**，否则两者会打架（每 tick 互相覆盖目标）。 | 巡逻、逃跑、寻找远处的目标、需要绕开方块和跨越障碍。 |
| **短距离、定向直驱** | `MoveControl.setWantedPosition(...)` | 直驱。它会计算到目标的向量，并**改变身体和头部的朝向**（`mob.setYRot`）向前走。 | 冲刺、冲向目标、短距离逼近。 |
| **战斗走位（侧移/后撤）** | `MoveControl.strafe(front, right)` | 以**自身当前朝向为基准**前后左右平移。**完全不影响身体和头部朝向**。 | 保持距离（风筝）、绕圈走位、一边后退一边看着玩家射击。 |

### ⚠️ 核心避坑：直驱 Goal 必须覆写 `requiresUpdateEveryTick`
`GoalSelector` 是隔 tick 检查的。如果你用 `setWantedPosition` 或 `strafe` 做直驱移动，你的 Goal **必须**覆写：
```java
@Override
public boolean requiresUpdateEveryTick() {
    return true; 
}
```
**原因**：`MoveControl.tick()` 每次执行完 `MOVE_TO` 或 `STRAFE` 后，会立即把状态重置为 `WAIT`。在下一个 tick，如果状态是 `WAIT`，它会执行 `mob.setZza(0)` 把速度清零。如果你不每 tick 发送移动指令，实体会走一下、停一下，平均速度腰斩。

---

## 2. 一 tick 的真实调用链

```java
LivingEntity.tick() {
    ...
    this.aiStep();                    // AI 都在这里跑
    ...
    this.tickHeadTurn(f1, f2);        // 身体/头部朝向协调
}

LivingEntity.aiStep() {
    ...
    if (this.isEffectiveAi())         // 服务端才跑
        this.serverAiStep();          // Mob 里是 final
    ...
    this.travel(vec31);               // == 真正位移 ==
}

// Mob.serverAiStep（final，不能被覆写）
Mob.serverAiStep() {
    int i = this.tickCount + this.getId();
    if (i % 2 != 0 && this.tickCount > 1) {
        this.targetSelector.tickRunningGoals(false);  // 只跑 requiresUpdateEveryTick 的
        this.goalSelector.tickRunningGoals(false);
    } else {
        this.targetSelector.tick();                   // 完整：canUse/canContinueToUse
        this.goalSelector.tick();
    }

    this.navigation.tick();          // == 每 tick ==
    this.customServerAiStep();       // 给子类留的钩子
    this.moveControl.tick();         // == 每 tick ==
    this.lookControl.tick();
    this.jumpControl.tick();
}
```

要点：

- **`serverAiStep` 是 `final`**。自定义逻辑写 `customServerAiStep()`。
- **普通 Goal 的 `tick()` 最多每 2 tick 一次**。需要每 tick 驱动的必须覆写 `requiresUpdateEveryTick`。
- **导航与控制器每 tick 都跑**，不受奇偶影响。
- `travel` 只在服务端执行普通 Mob 的权威位移。**客户端靠插值包，所以客户端要读取的状态（如动画标志）必须通过 `SynchedEntityData` 同步。**

---

## 3. GoalSelector 与 TargetSelector

**两者都是 `GoalSelector`**。`targetSelector` 只是专门放 `Flag.TARGET` 的 Goal 而已。

### 3.1 四种 Flag 与互斥

```java
public enum Flag { MOVE, LOOK, JUMP, TARGET; }
```
- Goal 通过 `setFlags(...)` 声明占用。
- **不同 Flag 互不冲突**（MOVE 和 LOOK 可同时跑），**同一 Flag 只能被一个 Goal 占用**。
- `TargetGoal` 统一占 `TARGET`。`MeleeAttackGoal` 占 `MOVE + LOOK`。

### 3.2 控制 Flag 的临时禁用
`Mob.tick()` 每 5 tick 会调 `updateControlFlags()`。如果实体被其他 Mob 骑乘，或坐在船上，`MOVE` / `JUMP` / `LOOK` Flag 会被禁用，对应的 Goal 会立刻 `stop()`。**"我的 Goal 没跑" 先查这里。**

### 3.3 目标选择 (TargetGoal)
- `TargetGoal` 默认不是每 tick 扫全图，而是有 `randomInterval`（通常 5 tick）降频。
- 判定标准：`FOLLOW_RANGE`（跟随距离）、视线（`hasLineOfSight`）、队伍判定。
- **先手反制**：若想让怪主动攻击把它设为目标的人，可用 `getEntitiesOfClass(Mob.class, bb, e -> e.getTarget() == this)` 扫出威胁，丢进高优先级的 TARGET Goal。

---

## 4. 控制器 Control 详解

### 4.1 MoveControl — 战术移动
- `setWantedPosition(x, y, z, speed)`：定向移动。会计算角度并让实体身体转向目标。如果目标高度差大于 `maxUpStep`，会自动跳跃。
- `strafe(forward, right)`：侧向平移。基于实体现有朝向，只改 `xxa` 和 `zza`，**不会让实体转头**。
- **注意**：`Mob.setSpeed` 会顺手 `setZza(speed)`（1.21 特性）。MOVE_TO 分支不需要再写 `setZza(1.0)`。

### 4.2 JumpControl — 跳跃
- `jump()` 只是打个标记。真正的起跳在 `aiStep` 里。必须在当 tick 的控制器阶段调用。

### 4.3 LookControl 与 BodyRotationControl — 视线与身体
- `LookControl` 只动头（`yHeadRot` 和 `xRot`），不动身体（`yBodyRot`）。
- `BodyRotationControl` 负责身体朝向与头部的协调（滞后跟随）。

---

## 5. PathNavigation 详解

**定位：仅用于路途较远，且路径复杂的情况。** 它的特征并非故意走走停停，而是为了实现绕障，必须将 A* 路径拆分成节点。

- `createPath(...)` 是**同步 A***。如果路径极长或复杂，会在当 tick 卡顿。
- 每 tick 它会把下一个节点的坐标通过 `MoveControl.setWantedPosition` 喂给控制器。
- 当实体到达节点判定距离（`maxDistanceToWaypoint`）后，推进到下一个节点。
- **卡住检测**：每 100 tick 检查一次位移，若位移不足则判定卡住并 `stop()`。
- **重算节流**：目标移动后，路径重算有 20 tick 的节流限制。

**死锁警告**：在 `PathNavigation` 运行时（`!isDone()`），你绝对不能在 Goal 里手写 `MoveControl.setWantedPosition`。导航会在每个 tick 覆盖你的指令。

---

## 6. 攻击 doHurtTarget

- **必须用 `Mob` 的 `doHurtTarget`**，统一攻击入口。
- **不检查距离**：调用前必须由 Goal 自己判定距离。
- **不控制冷却**：必须由 Goal 自己决定攻击时机。
- **不播动画**：必须自己调用动画函数，比如 `mob.swing(InteractionHand.MAIN_HAND)`。

---

## 7. 自定义 Goal 的姿势清单

1. **声明 Flag**：不占 MOVE 不动，不占 LOOK 不转头。
2. **覆写 requiresUpdateEveryTick**：直驱移动的 Goal 必须返回 `true`。
3. **做节流**：`canUse` 里别全图搜，加冷却；攻击前判距离和冷却。
4. **收尾**：`stop()` 里清理状态，`navigation.stop()`。

---

## 8. 常见坑速查

| 坑 | 症状 / 解释 |
|---|---|
| 覆写 `serverAiStep` | 它是 `final`，编译不过；写 `customServerAiStep` |
| 直驱 Goal 没开 `requiresUpdateEveryTick` | MoveControl 隔 tick 走 `WAIT` 分支把速度清零 -> 一卡一卡、平均速度减半 |
| 直驱和 A* 同时开 | `PathNavigation.tick()` 会覆盖 MoveControl 的 wanted；两者必须二选一 |
| Goal 没设置 Flag | 不占 MOVE 就不会移动；不占 LOOK 就不会转头 |
| MoveControl 直驱撞墙 | 它不绕障；卡住需用 `navigation.moveTo` 兜底一次 |
| 动态目标疯狂 `moveTo` | 每次同步跑 A*，代价高且导致走走停停 |
| 用 `doHurtTarget` 前没判距离/冷却 | 隔空打人、每 tick 狂打、无 swing |
| 跨端读 AI 状态 | `travel`/AI 只在服务端权威，客户端渲染必须读同步字段 |