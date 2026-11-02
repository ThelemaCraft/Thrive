package com.thelema.thrive.generic.item;

import com.thelema.thrive.generic.entity.projectile.WindBlade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber
public class BreezeSword extends Item {

    private static final int COOLDOWN = 8;   // 0.4 秒

    public BreezeSword() {
        super(new Item.Properties()
                .stacksTo(1)
                .component(DataComponents.TOOL, SwordItem.createToolProperties())
                .component(DataComponents.ATTRIBUTE_MODIFIERS,
                        ItemAttributeModifiers.builder()
                                .add(Attributes.ATTACK_DAMAGE,
                                        new AttributeModifier(
                                                Item.BASE_ATTACK_DAMAGE_ID, 7.0,
                                                AttributeModifier.Operation.ADD_VALUE),
                                        EquipmentSlotGroup.MAINHAND)
                                .add(Attributes.ATTACK_SPEED,
                                        new AttributeModifier(
                                                Item.BASE_ATTACK_SPEED_ID, -2.4F,
                                                AttributeModifier.Operation.ADD_VALUE),
                                        EquipmentSlotGroup.MAINHAND)
                                .build()));
    }

    // ───── 事件：左键发射 ─────

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent e) {
        tryShoot(e.getEntity());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        tryShoot(e.getEntity());
    }

    private static void tryShoot(Player player) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BreezeSword)) return;
        if (player.getCooldowns().isOnCooldown(stack.getItem())) return;

        WindBlade.shoot(sl, player, 1.5F);
        player.getCooldowns().addCooldown(stack.getItem(), COOLDOWN);
    }

    // ───── 剑行为 ─────

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;   // 发射走 AttackEntityEvent，这里只需返回 true 让 postHurtEnemy 生效
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // 无法破坏，无需扣耐久
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public boolean canPerformAction(ItemStack stack, net.neoforged.neoforge.common.ItemAbility ability) {
        return net.neoforged.neoforge.common.ItemAbilities.DEFAULT_SWORD_ACTIONS.contains(ability);
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.BREEZE_ROD);
    }
}