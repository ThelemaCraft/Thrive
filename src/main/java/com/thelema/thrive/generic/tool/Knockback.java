package com.thelema.thrive.generic.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Function;


public class Knockback {

    /**
     * 对以给定点为中心、半径内的实体施加爆炸式击退（无伤害）。
     * @param level     服务端世界
     * @param center    击退源中心（爆炸点）
     * @param radius    影响半径
     * @param strength  基础击退强度（距离中心越近越强）
     * @param predicate 额外筛选条件（返回 true 才被击退）
     * @return 被击退的实体列表
     */
    public static List<Entity> point(ServerLevel level, Vec3 center, double radius, double strength,
                                     Function<Entity, Boolean> predicate) {
        AABB aabb = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, aabb,
                e -> e.isAlive() && predicate.apply(e));

        for (Entity entity : entities) {
            double dx = entity.getX() - center.x;
            double dy = entity.getY() - center.y;
            double dz = entity.getZ() - center.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 0.01) continue;
            double factor = Math.max(0, strength * (1.0 - distance / radius));
            dx /= distance;
            dy /= distance;
            dz /= distance;
            // 添加垂直分量，产生弹跳效果
            entity.setDeltaMovement(entity.getDeltaMovement().add(dx * factor, dy * factor + 0.2, dz * factor));
            entity.hurtMarked = true;
        }
        return entities;
    }

    /**
     * 简化版本：仅使用 BlockPos 作为中心，默认半径 4.0，强度 1.2，不筛选任何实体。
     */
    public static List<Entity> point(ServerLevel level, Vec3 center) {
        return point(level, center, 4.0, 1.2, e -> true);
    }

    /**
     * 使用 BlockPos 作为中心（取方块中心 +0.5）。
     */
    public static List<Entity> point(ServerLevel level, BlockPos pos, double radius, double strength,
                                     Function<Entity, Boolean> predicate) {
        return point(level, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                radius, strength, predicate);
    }
}