# 实体 AI 系统详解

> 依据 `neoforge-21.1.233-merged.jar` 内反编译源码逐行核对。
> 原「AI系统」笔记的部分说法不准确，逐条校对见 **第 9 节**。
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

## 1. 一 tick 的真实调用链

```java
// Entity.tick -> LivingEntity.tick
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
    if (this.jumping && this.isAffectedByFluids()) { ... jumpFromGround() ... }
    this.xxa *= 0.98F;
    this.zza *= 0.98F;
    Vec3 vec31 = new Vec3(this.xxa, this.yya, this.zza);
    this.travel(vec31);               // == 真正位移 ==
}

// Mob.serverAiStep（final，不能被覆写）
Mob.serverAiStep() {
    this.noActionTime++;
    this.sensing.tick();

    int i = this.tickCount + this.getId();
    if (i % 2 != 0 && this.tickCount > 1) {
        this.targetSelector.tickRunningGoals(false);  // 只跑 requiresUpdateEveryTick 的
        this.goalSelector.tickRunningGoals(false);
    } else {
        this.targetSelector.tick();                   // 完整：canUse/canContinueToUse + start/stop
        this.goalSelector.tick();
    }

    this.navigation.tick();          // == 每 tick ==
    this.customServerAiStep();       // 给子类留的钩子（自定义 AI 写这里）
    this.moveControl.tick();         // == 每 tick ==
    this.lookControl.tick();
    this.jumpControl.tick();
    this.sendDebugPackets();
}
```

要点：

- **`serverAiStep` 是 `final`**。不要覆写它；自定义逻辑写 `customServerAiStep()`。
- **目标/行为选择不是每 tick**：`(tickCount + id) % 2 == 0` 才完整 `tick()`，另一 tick 只跑 `requiresUpdateEveryTick()` 的 Goal。所以普通 Goal 的 `tick()` **最多每 2 tick 一次**。
- **导航与控制器每 tick 都跑**，不受上面的奇偶影响。
- `bodyRotationControl.clientTick()` 在 `Mob.tickHeadTurn()` 里调用，而 `tickHeadTurn` 由 `LivingEntity.tick()` 两端都会调。名字叫 client，实际两端都过，只负责头部/身体的视觉协调。
- `travel` 只在 `isControlledByLocalInstance()` 为真时执行：
  - 服务端：普通 Mob 为真 -> 服务端权威位移。
  - 客户端：别人的 Mob 为假 -> 不走 `travel`，靠位置插值包。**所以 AI 逻辑放服务端，客户端要读的状态走同步位。**

---

## 2. GoalSelector 与 TargetSelector

**两者都是 `GoalSelector`**。`targetSelector` 只是专门放 `Flag.TARGET` 的 Goal 而已，机制完全一样。

### 2.1 添加与优先级

```java
goalSelector.addGoal(priority, goal);   // 数字越小优先级越高
```

`WrappedGoal.canBeReplacedBy(other)`：

```java
return this.isInterruptable() && other.getPriority() < this.getPriority();
```

- 优先级只在**抢占**时比较：高优先级（小数字）可以挤掉正在跑的低优先级 Goal。
- `Goal.isInterruptable()` 默认 `true`；返回 `false` 可以做成"不可打断"，低优先级永远抢不走它。

### 2.2 四种 Flag 与互斥

```java
public enum Flag { MOVE, LOOK, JUMP, TARGET; }
```

- Goal 通过 `setFlags(EnumSet.of(...))` 声明自己要占用哪些 Flag。
- `GoalSelector.lockedFlags` 记录**每种 Flag 当前被哪个 Goal 占着**。
- 一个 Goal 只有在它的**所有** Flag 都空闲、或能被它抢占时，才能 `start()`。
- 不同 Flag 互不冲突，所以一个 MOVE Goal 和一个 LOOK Goal 可以同时跑；同一个 Flag 同一时刻只能有一个 Goal。
- `TargetGoal` 们统一 `setFlags(EnumSet.of(Flag.TARGET))`。
- `MeleeAttackGoal` 声明 `MOVE + LOOK`。

### 2.3 tick 时序（重要）

`GoalSelector.tick()` 内部顺序：

```
goalCleanup   : 正在跑但 canContinueToUse()==false 或 Flag 被禁 -> stop()
goalUpdate    : 遍历所有未运行 Goal；Flag 空闲/可替换且 canUse() -> stop 旧 Goal、start 自己
goalTick      : tickRunningGoals(true) -- 所有正在跑的 Goal 都 tick()
```

> `tickRunningGoals(true)` 表示"全部跑"。而 `Mob.serverAiStep` 在奇数 tick 调的是 `tickRunningGoals(false)`，只跑 `requiresUpdateEveryTick()` 的。**所以 `requiresUpdateEveryTick` 只影响"隔的那个 tick"，不是决定 tick 与否的总开关。**

> **直驱类 Goal 必须 `requiresUpdateEveryTick() = true`。** 因为 `MoveControl.tick()` 一进 `MOVE_TO` 就把 `operation` 重置回 `WAIT`：
> ```java
> } else if (this.operation == Operation.MOVE_TO) {
>     this.operation = Operation.WAIT;   // 立刻复位
>     ... setSpeed/setYRot ...
> } else {
>     this.mob.setZza(0.0F);             // WAIT -> 清零，实体站住
> }
> ```
> 如果 Goal 隔 tick 才 `setWantedPosition` 一次，那么"隔的那个 tick" MoveControl 处于 `WAIT`，`zza` 被清 0，实体就会一卡一卡、平均速度减半。本项目 `KitingGoal` 第一版就是踩了这个坑。

### 2.4 控制 Flag 会被系统临时禁用（每 5 tick）

`Mob.tick()`：

```java
if (!this.level().isClientSide && this.tickCount % 5 == 0)
    this.updateControlFlags();
```

```java
boolean flag  = !(this.getControllingPassenger() instanceof Mob); // 没被其它 Mob 骑
boolean flag1 = !(this.getVehicle() instanceof Boat);            // 不在船上
this.goalSelector.setControlFlag(Flag.MOVE, flag);
this.goalSelector.setControlFlag(Flag.JUMP, flag && flag1);
this.goalSelector.setControlFlag(Flag.LOOK, flag);
```

被禁用 Flag 的 Goal 会立刻 `stop()`。**"我的 Goal 没跑" 先查这里。**

### 2.5 目标选择

- `TargetGoal.canContinueToUse()` 里会 `mob.setTarget(target)`；`stop()` 里 `setTarget(null)`。
- 目标存 `mob.getTarget()`，供行为 Goal 读。
- `NearestAttackableTargetGoal` 的 `canUse` 用 `randomInterval` 降频（默认 `reducedTickDelay(10)` = 5），不是每 tick 全图扫。
- `mustReach` 的 Goal 会 `mob.getNavigation().createPath(target, 0)` 做可达性预检，代价不低。
- `TargetGoal` 会检查：`canAttack`、同队、跟随距离 `FOLLOW_RANGE`、视线（`mustSee`）、`unseenMemoryTicks`（默认 60）。
- `getSensing().hasLineOfSight(entity)` 是视线判定的正解。

**先手目标选择（反制"被打了才还手"）**

`HurtByTargetGoal` 只在 `lastHurtByMob` 被设置后才反应，对上慢吞吞逼近的铁傀儡会吃亏。
要主动，就自己扫"谁把我设成了目标"：

```java
witch.level().getEntitiesOfClass(LivingEntity.class,
        witch.getBoundingBox().inflate(followRange),
        e -> e instanceof Mob mob && mob.getTarget() == witch && witch.canAttack(e));
```

- 取**最近**的一个（多敌人时避免盯着远处那个，被贴脸的偷）；
- 放进高优先级（数字小）的 `TARGET` Goal，就能压过 `HurtByTargetGoal` / `NearestAttackableTargetGoal`；
- 注意 `Mob.getTarget()` 只反映 Goal 系统的目标；纯 Brain 怪的目标在 `brain` 的 `ATTACK_TARGET` 记忆里，不能对任意 Mob 直接 `getBrain().getMemory(...)`（未注册的记忆类型会抛异常）。

本项目活例：`NearestThreatTargetGoal`。

---

## 3. 控制器 Control

`Control` 是个空接口，纯标记。四个实现，字段和 tick 行为如下。

### 3.1 MoveControl -- 战术移动

字段：

| 字段 | 含义 |
|---|---|
| `mob` | 宿主 |
| `wantedX/Y/Z` | 目的坐标 |
| `speedModifier` | 速度系数（会乘 MOVEMENT_SPEED 属性） |
| `strafeForwards` / `strafeRight` | STRAFE 模式的前后/左右输入 |
| `operation` | `WAIT` / `MOVE_TO` / `STRAFE` / `JUMPING` |

API：

```java
moveControl.setWantedPosition(x, y, z, speed);  // 设目的与速度系数，operation=MOVE_TO
moveControl.strafe(forward, right);             // operation=STRAFE
moveControl.hasWanted();                        // operation==MOVE_TO
```

`tick()` 做的事：

- `WAIT`：`mob.setZza(0)`（刹停）。
- `MOVE_TO`：
  1. 算水平方向角，`mob.setYRot(rotlerp(当前, 目标, 90))` -- **每 tick 最多转 90 度**。
  2. `mob.setSpeed(speedModifier * MOVEMENT_SPEED)`。
  3. 目的比 `maxUpStep()` 高、或正前方是碰撞方块/门/栅栏 -> `mob.getJumpControl().jump()`，切 `JUMPING`。
  4. 到达判定用平方距离 `< 2.5e-7`（极小）。
- `JUMPING`：保持 speed，落地后回 `WAIT`。
- `STRAFE`：把 forward/right 投影到朝向，`setSpeed` + `setZza` + `setXxa`，走一步后回 `WAIT`。

> **`MoveControl.tick()` 本身不位移**。它只写 `yRot / speed / zza / xxa`；真正位移在同 tick 后面的 `travel()`。

**坑：`Mob.setSpeed` 会顺手 `setZza(speed)`。**

```java
// Mob
@Override public void setSpeed(float speed) {
    super.setSpeed(speed);
    this.setZza(speed);        // == 1.21 的实际行为 ==
}
```

所以 MoveControl 的 MOVE_TO 分支**不需要**再写 `setZza(1.0)`；网上很多旧图示里 `setSpeed(0.5) + setZza(1.0)` 与现代源码不符。

`MoveControl` 不绕障：撞墙只会尝试跳。绕障要么靠 `PathNavigation`，要么自己用 `navigation.moveTo` 兜底。

### 3.2 JumpControl -- 跳跃

```java
public void jump() { this.jump = true; }        // 只标记
public void tick()  { this.mob.setJumping(this.jump); this.jump = false; }
```

- `jump()` 只是标记；`tick()` 把标记写进 `mob.jumping` 后清零。
- 真正的起跳在 `LivingEntity.aiStep()` 里：`if (this.jumping && isAffectedByFluids()) ... jumpFromGround()/jumpInFluid()`。
- 想让它跳：`mob.getJumpControl().jump()`，且必须在它当 tick 的 `aiStep` 之前（即控制器 tick 阶段）调用。

### 3.3 LookControl -- 视线

字段：`yMaxRotSpeed`、`xMaxRotAngle`、`lookAtCooldown`、`wantedX/Y/Z`。

```java
lookControl.setLookAt(entity, yawSpeed, pitchSpeed); // lookAtCooldown = 2
```

`tick()`：

- 冷却中：把 `mob.yHeadRot` 按 `yMaxRotSpeed` 转向目标；把 `mob.xRot`（俯仰）按 `xMaxRotAngle` 转向目标。
- 冷却结束：`yHeadRot` 缓慢回正到 `yBodyRot`（10 度/tick）。
- 导航进行中时 `clampHeadRotationToBody()`，把头部夹在身体最大偏角内（`getMaxHeadYRot()`）。

> LookControl 只动 `yHeadRot` / `xRot`，**不动 `yBodyRot`**。身体朝向归 BodyRotationControl 管。

### 3.4 BodyRotationControl -- 身体朝向

- 入口 `clientTick()`，由 `Mob.tickHeadTurn()` 调用。
- 逻辑：移动时身体正对 `yRot`，头夹在身体范围内；静止时头先转、身体延迟 10 tick 再慢慢跟上。
- 名字带 client，但随 `LivingEntity.tick` 两端都跑，属于视觉/表现层，不参与寻路。

---

## 4. 移动三层模型

| 场景 | 用什么 | 特征 |
|---|---|---|
| 到固定点、要绕障、穿门爬梯 | `PathNavigation.moveTo(...)` | A* 逐节点，天然绕障，节点处易顿 |
| 目标点每 tick 变（追击/风筝） | `MoveControl.setWantedPosition(...)` | 直驱，丝滑，不绕障 |
| 飞行/无重力 | `MoveControl` | 同上 |

### 4.1 案例：村民逃跑为什么丝滑

村民没有用 goalSelector 跑，而是 Brain 的 PANIC 活动：

```java
// VillagerGoalPackages.getPanicPackage
SetWalkTargetAwayFrom.entity(NEAREST_HOSTILE, speedModifier * 1.5F, 6, false)
SetWalkTargetAwayFrom.entity(HURT_BY_ENTITY,   speedModifier * 1.5F, 6, false)
```

- `SetWalkTargetAwayFrom` 在威胁进入 `desiredDistance` 后，调用
  `LandRandomPos.getPosAway(mob, 16, 7, threatPos)` 选一个**真实可站立的陆地坐标**，
  存进 `WALK_TARGET` 记忆（不是每 tick 重挑）。
- `MoveToTargetSink`（CORE 行为）把 `WALK_TARGET` 变成一次
  `navigation.createPath(...)` + `navigation.moveTo(path, speed)`；只有目标移动 >2 格、
  路径走完或卡住时才重算（卡住还有 40 tick 冷却）。
- 移动全交给 `PathNavigation`：每 tick 推进、天然绕障/跳台阶/开门。

**丝滑的三个关键**
1. 目标是**真实陆地坐标**（`LandRandomPos` 会过滤悬空/实心），不会指向墙里；
2. **一次选点、走到再换**，不每 tick 重算 A*；
3. 用 `navigation` 而不是裸 `MoveControl`，绕障/跳跃/卡住检测都是现成的。

本项目 `KitingGoal` 现在就是这套：`LandRandomPos.getPosAway` 选点 + `navigation.moveTo`，
`repathTimer` 1 秒一次决策，但只在没路径时才重新 A*。

**永远不要同时让 `navigation` 和手写 `MoveControl` 抢同一个 MOVE Flag**：两者都会 `setWantedPosition`，会互相打架。要么只用导航，要么"直驱为主、卡住才 `navigation.moveTo` 兜底一次"（见第 8 节）。

---

## 5. PathNavigation 详解

`PathNavigation` 是抽象基类；地面生物用 `GroundPathNavigation`（`Mob.createNavigation` 默认返回它）。

### 5.1 建路径 = 同步 A*

```java
public boolean moveTo(double x, double y, double z, double speed) {
    return this.moveTo(this.createPath(x, y, z, 1), speed);
}
public boolean moveTo(Entity entity, double speed) {
    Path path = this.createPath(entity, 1);
    return path != null && this.moveTo(path, speed);
}
```

`createPath(...)` 里直接 `pathFinder.findPath(...)`，**同步执行**，没有"后台 A* 跑几 tick"这回事。代价体现在当 tick 卡顿，不是实体静止若干 tick。

### 5.2 每 tick 推进

```java
public void tick() {
    this.tick++;
    if (this.hasDelayedRecomputation) this.recomputePath();
    if (!this.isDone()) {
        if (this.canUpdatePath()) this.followThePath();
        ...
        if (!this.isDone()) {
            Vec3 next = this.path.getNextEntityPos(this.mob);
            this.mob.getMoveControl().setWantedPosition(next.x, this.getGroundY(next), next.z, this.speedModifier);
        }
    }
}
```

- **MoveControl 的 wanted 点 = 下一路径节点**，不是最终目标。
- `followThePath()` 用 `maxDistanceToWaypoint`（默认 0.5，或按体型 `bbWidth` 调）判断"到节点了" -> `path.advance()`。
- 到了节点附近，MoveControl 会减速/刹停，等 `advance()` 再给下一个点 -> **每节点一个启停，这就是"走走停停"的主因**。

### 5.3 常用 API

| 方法 | 说明 |
|---|---|
| `moveTo(x,y,z,speed)` / `moveTo(entity,speed)` | 建路径并走；失败返回 false |
| `stop()` | **只把 `path = null`**，不清速度；下 tick `isDone()` 为 true，MoveControl 因没新目标而停 |
| `isDone()` | `path == null || path.isDone()` |
| `isInProgress()` | `!isDone()` |
| `setSpeedModifier(double)` | 存起来，tick 里乘进 `setWantedPosition` |
| `setCanFloat(boolean)` | 透传 `nodeEvaluator.setCanFloat`，影响水/游泳寻路 |
| `setMaxVisitedNodesMultiplier(float)` | A* 节点预算倍率，越大越能绕远路也越贵 |
| `recomputePath()` | 20 tick 节流；超时才重算，否则挂 `hasDelayedRecomputation` 下 tick 再说 |
| `shouldRecomputePath(BlockPos)` | 按路径终点与自己中点距离判断要不要重算 |

### 5.4 卡住检测

`doStuckDetection` 每 **100 tick** 检查一次：位移不足 `speed*100*0.25` -> `isStuck=true` 并 `stop()`。另有按节点的超时 `timeoutPath()`。**"实体卡住了" 要区分是 MoveControl 直驱卡墙，还是导航自己判卡。**

### 5.5 走走停停的四个真实原因

1. **逐节点驱动**：wanted 点是节点，不是终点；节点处必刹停。
2. **频繁 `moveTo`**：每次重新同步跑 A*，目标点变一次重算一次，还可能返回 null -> `path=null` -> `isDone()`。
3. **20 tick 重算节流**：`recomputePath` 不是立即执行，配合 `hasDelayedRecomputation` 会有 1 tick 空窗。
4. **卡住检测/节点超时**：判卡会 `stop()`，表现为突然站住，之后重新起步。

### 5.6 GroundPathNavigation 补充

- `canUpdatePath()`：`onGround() || isInLiquid() || isPassenger()`。**腾空时不推进路径**。
- `getGroundY(vec)` 把目标 Y 修正到地面/水面高度。
- `createPath(BlockPos)` 会处理悬空/实心方块，把目标挪到可站立的 Y。
- `setCanOpenDoors` / `setCanPassDoors` / `setAvoidSun` / `setCanWalkOverFences`。

---

## 6. 攻击 doHurtTarget

### 6.1 两个版本

```java
// LivingEntity：几乎是空壳，只记 lastHurtMob
public boolean doHurtTarget(Entity target) {
    this.setLastHurtMob(target);
    return false;
}
```

```java
// Mob：真正的伤害逻辑
public boolean doHurtTarget(Entity entity) {
    float f = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    DamageSource src = this.damageSources().mobAttack(this);
    if (level instanceof ServerLevel sl)
        f = EnchantmentHelper.modifyDamage(sl, this.getWeaponItem(), entity, src, f);
    boolean hit = entity.hurt(src, f);
    if (hit) {
        float kb = this.getKnockback(entity, src);
        if (kb > 0 && entity instanceof LivingEntity le) le.knockback(...);
        if (level instanceof ServerLevel sl) EnchantmentHelper.doPostAttackEffects(sl, entity, src);
        this.setLastHurtMob(entity);
        this.playAttackSound();
    }
    return hit;
}
```

结论：

- **用 `Mob.doHurtTarget`**（`Monster`、`Animal` 都继承到了）。
- **不检查距离**：调用方必须自己判。`Mob.isWithinMeleeAttackRange(target)` 是标准判法：
  - `getAttackBoundingBox().intersects(target.getHitbox())`
  - 盒子按 `DEFAULT_ATTACK_REACH = sqrt(2.04) - 0.6 ~ 0.83` 外扩。
- **不检查维度**：源码里没有任何 dimension 判断；跨维度目标在正常 Goal 下取不到，但调用方不能假设它帮你挡了。
- **不做 attack cooldown**：节流是 Goal 的责任（见 `MeleeAttackGoal.attackInterval = 20`）。
- **不播 swing 动画**：必须自己 `mob.swing(InteractionHand.MAIN_HAND)`（它触发 `attackAnim`）。
- 伤害来源 `damageSources().mobAttack(this)`；击退来自 `getKnockback` + `ATTACK_KNOCKBACK` 属性。
- `playAttackSound()` 默认空实现，需要就覆写。

### 6.2 标准近战 Goal 模板（照抄 `MeleeAttackGoal` 的核心）

```java
this.mob.getNavigation().moveTo(target, speed);      // 移动
...
if (this.ticksUntilNextAttack <= 0
        && this.mob.isWithinMeleeAttackRange(target)
        && this.mob.getSensing().hasLineOfSight(target)) {
    this.ticksUntilNextAttack = 20;
    this.mob.swing(InteractionHand.MAIN_HAND);
    this.mob.doHurtTarget(target);
}
```

`MeleeAttackGoal` 额外还有：`canUse` 20 tick 冷却、可选 `canPenalize` 可达性惩罚、每隔 4~11 tick 重算路径。

### 6.3 属性默认值（`Mob` / `Monster`）

| 属性 | 默认 |
|---|---|
| MAX_HEALTH | 20 |
| MOVEMENT_SPEED | 0.7（`Mob.createMobAttributes` 继承自 `LivingEntity`） |
| ATTACK_DAMAGE | 2（`Monster.createMonsterAttributes` 里加） |
| FOLLOW_RANGE | 16（`Mob.createMobAttributes` 覆盖了通用默认 32） |
| ATTACK_KNOCKBACK / KNOCKBACK_RESISTANCE | 0 |

---

## 7. 自定义 Goal 的正确姿势

```java
public class MyGoal extends Goal {
    public MyGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));     // 声明占用哪些 Flag
    }
    @Override public boolean canUse() { ... }           // 能否开始
    @Override public boolean canContinueToUse() { ... } // 默认复用 canUse
    @Override public void start() { ... }
    @Override public void stop()  { ... }               // 收尾：停导航、清状态
    @Override public boolean requiresUpdateEveryTick() { return true; } // 需要每 tick 就重写
    @Override public void tick() { ... }
}
```

清单：

1. **距离检查**：自己判，`doHurtTarget` 不管。
2. **攻击节流**：自己维护冷却（如 20 tick）。
3. **动画**：`mob.swing(hand)`（原版）或同步位触发 GeckoLib 动画。
4. **音效**：`level().playSound(...)` 或覆写 `playAttackSound()`。
5. **Flag 别乱占**：只想看就只占 LOOK；想走就占 MOVE；否则会和别人的 Goal 打架。
6. **收尾**：`stop()` 里 `navigation.stop()`、清自定义状态，避免状态残留。
7. **性能**：`canUse` 里别每 tick 全图搜索；用 20 tick 冷却或 `randomInterval` 降频。
8. **优先级**：越"核心/紧急"的越靠前（数字越小）。

---

## 8. 本项目的直驱风筝实现

`KitingGoal`（`GardenWitch` 用）刻意**不用 `navigation`**，改为直驱：

- 每 20 tick 选一次落脚点，期间 `dest` 固定 -> `MoveControl` 一口气走完，避免每 tick 重算抖动。
- 落脚点落在"以目标为圆心、NEAR~FAR 为半径"的圆上，方向沿"目标 -> 自己"。
- 在内侧去外圈、在外侧回内圈 -> 始终在走，距离夹在 NEAR~FAR。
- 卡墙兜底：1 秒没位移 -> `navigation.moveTo(dest)` 跑一次 A*，跑完继续直驱（`navigation.isDone()` 才写 `moveControl`，避免打架）。

判据：

| 场景 | 选 |
|---|---|
| 巡逻、固定点、绕障、穿门 | `navigation` |
| 追击、风筝、目标点每 tick/每秒变 | `MoveControl` 直驱 |
| 直驱撞墙 | 卡住 N tick 后用 `navigation.moveTo` 兜一次 |

---

## 9. 原「AI系统」笔记校对

| 原笔记说法 | 判定 | 修正 |
|---|---|---|
| 行为"每 tick 检查" | [错误] | `goalSelector.tick()`/`targetSelector.tick()` 隔 tick；另一 tick 只跑 `requiresUpdateEveryTick` |
| "各行为会独立" | [错误] | 按 `Goal.Flag` 互斥、按优先级抢占，不是独立 |
| 行为种类 MOVE/LOOK/JUMP/TARGET，不同类互不影响 | [正确] | 但一个 Goal 可占多个 Flag |
| 目标选择每 tick 检查 | [错误] | 同 goalSelector，隔 tick；且 target 也是 Goal |
| `travel` 是实际更新坐标、在 `LivingEntity` | [正确] | 另有 `isControlledByLocalInstance()` 门禁 |
| Control 在 Mob 里 tick | [正确] | `moveControl/lookControl/jumpControl` 每 tick，都在 `serverAiStep` |
| MoveControl 有目的坐标、速度系数、移动状态 | [正确] | 还有 `strafeForwards/strafeRight`、`operation` |
| MoveControl "tick 实现一次微元移动" | [错误] | 只设参数，位移在 `travel` |
| JumpControl "tick 仅把 jumping 设 true" | [注意] | 是 `setJumping(标记值)` 后清零；真正起跳在 `aiStep` |
| LookControl 管视线 | [正确] | 只动 `yHeadRot`/`xRot`；身体朝向归 BodyRotationControl |
| BodyRotationControl "客户端专用" | [注意] | 方法名 `clientTick`，实际随 `LivingEntity.tick` 两端都跑 |
| PathNavigation 的 moveTo/isDone/... 方法 | [正确] | 都在；`isInProgress = !isDone` |
| 图中 `setSpeed(0.5)` 后 `setZza(1.0)` | [错误] | `Mob.setSpeed` 已 `setZza(speed)` |
| "A* 期间 path 为空 -> 静止 1~2 tick" | [错误] | A* 同步执行；真正原因是逐节点 wanted 点 + `moveTo` 重算 + 重算节流 |
| `doHurtTarget` 自动伤害源/击退/返回命中/记目标 | [正确] | 但要 `Mob` 版本；`LivingEntity` 版是空壳 |
| `doHurtTarget` "同一维度" | [错误] | 源码没有维度检查 |
| "无视距离" | [正确] | 正因如此必须由 Goal 判距离 |
| 攻击需要：距离检查 / 状态标记 / 音效 | [正确] | 另需 `swing()` 动画与攻击冷却 |

---

## 10. 常见坑速查

| 坑 | 症状 / 解释 |
|---|---|
| 覆写 `serverAiStep` | 它是 `final`，编译不过；写 `customServerAiStep` |
| 以为 Goal 每 tick tick | 普通 Goal 隔 tick；需要每 tick 就 `requiresUpdateEveryTick() = true` |
| 直驱 Goal 没开 `requiresUpdateEveryTick` | MoveControl 隔 tick 走 `WAIT` 分支把 zza 清零 -> 一卡一卡、平均速度减半 |
| `dest.y` 和当前 Y 相同 | MoveControl 的 `d2 > maxUpStep()` 判不出台阶，不会自动跳；要让落脚点 Y 反映目标高度，或卡住时手动 `jumpControl.jump()` |
| 被打飞后还留着 A* 路线 | 空中 `canUpdatePath()==false`，旧路径不推进，落地后会顺着旧节点跑回原地；腾空时应 `navigation.stop()` |
| 直驱和 A* 同时开 | `PathNavigation.tick()` 在 Goal 之后跑，有 path 时每 tick 覆盖 MoveControl 的 wanted；两者必须二选一 |
| Goal 没设置 Flag | 不占 MOVE 就不会移动；不占 LOOK 就不会转头 |
| Flag 被系统禁用 | 每 5 tick `updateControlFlags`；被 Mob 骑/坐船时 MOVE/JUMP/LOOK 关闭 |
| 想移动却用 `lookControl` | 视线不产生位移 |
| MoveControl 直驱撞墙 | 它不绕障；卡住用 `navigation.moveTo` 兜底 |
| `navigation` + 手写 MoveControl 同时抢 | 两边都 `setWantedPosition`，互相打架 |
| 动态目标疯狂 `moveTo` | 每次同步 A*，代价高且导致走走停停 |
| 用 `doHurtTarget` 前没判距离/冷却 | 隔空打人、每 tick 狂打、无 swing |
| 用 `LivingEntity.doHurtTarget` | 返回 false、没伤害；要用 `Mob` 的 |
| 跨端读 AI 状态 | `travel`/AI 只在服务端权威，客户端靠插值；渲染要读的字段必须同步 |
| 飞行生物用 `GroundPathNavigation` | 地面导航不适用；用飞行导航/直驱，并覆写 `checkFallDamage` |
| `FOLLOW_RANGE` 记成 32 | 那是通用属性默认；`Mob.createMobAttributes` 已设 16 |
