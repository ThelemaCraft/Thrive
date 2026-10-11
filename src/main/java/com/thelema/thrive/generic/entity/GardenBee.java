package com.thelema.thrive.generic.entity;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.blockentity.GardenHiveBlockEntity;
import com.thelema.thrive.generic.entity.goal.ConvertHiveGoal;
import com.thelema.thrive.register.EntityRegister;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

@EventBusSubscriber
public class GardenBee extends Bee {

    /** 偏好花（Item）。null = 未驯服。 */
    @Nullable public Item favoriteFlower;

    /** 携带花粉的花源。null = 无。 */
    @Nullable public Item carriedPollen;

    /** 驯服者。null = 未绑定。 */
    @Nullable public UUID ownerUUID;

    public GardenBee(EntityType<? extends Bee> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new ConvertHiveGoal(this));
    }

    @Override
    public boolean wantsToEnterHive() {
        // 没找到 GardenHive 之前，不进任何巢
        if (!(level().getBlockEntity(getHivePos()) instanceof GardenHiveBlockEntity)) return false;
        return super.wantsToEnterHive();
    }

    // ==================== NBT ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (favoriteFlower != null) tag.putString("FavoriteFlower", key(favoriteFlower));
        if (carriedPollen != null) tag.putString("CarriedPollen", key(carriedPollen));
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("FavoriteFlower")) favoriteFlower = parse(tag.getString("FavoriteFlower"));
        if (tag.contains("CarriedPollen")) carriedPollen = parse(tag.getString("CarriedPollen"));
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
    }

    private static String key(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    @Nullable
    private static Item parse(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    // ==================== 驯服 ====================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (favoriteFlower != null) return super.mobInteract(player, hand);

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !stack.is(ItemTags.FLOWERS)) return super.mobInteract(player, hand);

        if (!level().isClientSide) {
            if (carriedPollen != null) shakeOffPollen();

            this.favoriteFlower = stack.getItem();
            this.ownerUUID = player.getUUID();
            if (!player.isCreative()) stack.shrink(1);
            playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** 清除携带的花粉，洒 DUST 粒子。 */
    private void shakeOffPollen() {
        this.carriedPollen = null;
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.25F), 1.0F),
                    getX(), getY() + 0.5, getZ(),
                    20, 0.3, 0.3, 0.3, 0.05);
        }
        playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
    }

    // ==================== 蜇人不死 ====================

    @Override
    public boolean doHurtTarget(Entity target) {
        DamageSource src = this.damageSources().sting(this);
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean hit = target.hurt(src, dmg);

        if (hit) {
            if (target instanceof LivingEntity living) {
                living.setStingerCount(living.getStingerCount() + 1);
                int poisonSec = switch (level().getDifficulty()) {
                    case NORMAL -> 10;
                    case HARD -> 18;
                    default -> 0;
                };
                if (poisonSec > 0) {
                    living.addEffect(new MobEffectInstance(MobEffects.POISON, poisonSec * 20, 0), this);
                }
            }
            playSound(SoundEvents.BEE_STING, 1.0F, 1.0F);
            // 不调 setHasStung(true)、不调 stopBeingAngry()
        }
        return hit;
    }

    // ==================== 群系转化 ====================

    /** 花园群系。原版蜜蜂踏入即转化。 */
    static final ResourceKey<Biome> GARDEN = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "garden"));

    /**
     * 原版蜜蜂进入花园群系  转成花园蜜蜂。
     * NBT 整体搬运，保留血量、蜂工、采蜜进度；"已蜇"不继承花园蜜蜂蜇人不死。
     */
    @SubscribeEvent
    public static void onBeeTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Bee bee) || bee.getClass() != Bee.class) return;
        if (!(bee.level() instanceof ServerLevel level)) return;
        if (!level.getBiome(bee.blockPosition()).is(GARDEN)) return;

        GardenBee garden = EntityRegister.GARDEN_BEE.get().create(level);
        if (garden == null) return;

        CompoundTag tag = bee.saveWithoutId(new CompoundTag());
        tag.remove(Bee.TAG_HAS_STUNG);
        tag.remove("UUID");   // 不能继承旧 UUID，否则撞上还在场上的旧蜜蜂，addFreshEntity 会被拒
        garden.load(tag);

        level.addFreshEntity(garden);
        bee.discard();
    }
}
