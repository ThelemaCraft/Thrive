package com.thelema.thrive.generic.tool;

import com.thelema.thrive.ThriveMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** 绕过原版伤害免疫帧，同刻多次伤害分 tick 依次全额结算。 */
@EventBusSubscriber(modid = ThriveMod.MOD_ID)
public final class Damage {

    private static final Map<ResourceKey<Level>, Map<UUID, List<Entry>>> QUEUES = new HashMap<>();

    private record Entry(ResourceKey<DamageType> type,
                         @Nullable UUID direct,
                         @Nullable UUID causing,
                         @Nullable Vec3 pos,
                         float amount) {}

    private Damage() {}

    private static Map<UUID, List<Entry>> queue(ServerLevel lv) {
        return QUEUES.computeIfAbsent(lv.dimension(), k -> new HashMap<>());
    }

    public static void hurt(LivingEntity target, DamageSource ds, float v) {
        if (target.level().isClientSide || v <= 0) return;
        var typeKey = ds.typeHolder().unwrapKey().orElse(null);
        if (typeKey == null) return;
        Entity d = ds.getDirectEntity();
        Entity c = ds.getEntity();
        queue((ServerLevel) target.level())
                .computeIfAbsent(target.getUUID(), k -> new ArrayList<>())
                .add(new Entry(typeKey,
                        d == null ? null : d.getUUID(),
                        c == null ? null : c.getUUID(),
                        ds.getSourcePosition(), v));
    }

    public static void hurt(LivingEntity target, String type, float v) {
        hurt(target, type, null, null, null, v);
    }

    public static void hurt(LivingEntity target, String type,
                            @Nullable Entity direct,
                            @Nullable Entity causing,
                            @Nullable Vec3 pos,
                            float v) {
        var key = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse(type));
        Holder<DamageType> holder = target.level().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
        hurt(target, new DamageSource(holder, direct, causing, pos), v);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        Set<ResourceKey<Level>> active = new HashSet<>();

        for (ServerLevel lv : e.getServer().getAllLevels()) {
            active.add(lv.dimension());
            var map = QUEUES.get(lv.dimension());
            if (map == null || map.isEmpty()) continue;

            for (UUID id : List.copyOf(map.keySet())) {
                var list = map.get(id);
                var le = lv.getEntity(id) instanceof LivingEntity l ? l : null;

                // 死亡 / 卸载 / 空列表 → 清
                if (list == null || list.isEmpty() || le == null || !le.isAlive()) {
                    map.remove(id);
                    continue;
                }

                // 本 tick 刚被原版打过，让一让
                if (le.invulnerableTime == 20) continue;

                // 创造玩家：消耗队首，不实际施伤
                boolean skip = le instanceof ServerPlayer sp && sp.isCreative();

                var h = list.get(0);
                if (!skip) {
                    var holder = lv.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                            .get(h.type).orElse(null);
                    if (holder != null) {
                        // 残余无敌帧压到 10，走全额分支
                        if (le.invulnerableTime > 10) le.invulnerableTime = 10;
                        var ds = new DamageSource(holder,
                                h.direct == null ? null : lv.getEntity(h.direct),
                                h.causing == null ? null : lv.getEntity(h.causing),
                                h.pos);
                        le.hurt(ds, h.amount);
                    }
                }
                list.remove(0);
                if (list.isEmpty()) map.remove(id);
            }
        }

        // 卸载的维度，清
        QUEUES.keySet().retainAll(active);
    }
}