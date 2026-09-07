package com.example.clearitems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Жезл Магнита (Magnet Wand).
 * При правом клике по любому блоку притягивает все лежащие предметы
 * (ItemEntity) в радиусе к игроку — они «летят» к нему, а не телепортируются,
 * и собираются в инвентарь обычным подбором (со стеками и merge).
 *
 * Whitelist из ClearItemsConfig уважается: защищённые дропы остаются на месте.
 *
 * Радиус настраивается циклически по Shift+ПКМ в воздухе (как у Жезла Очистки).
 */
public class MagnetWandItem extends Item {

    private static final int[] RADIUS_STEPS = {8, 16, 32, 50};
    private static final int DEFAULT_RADIUS = 16;
    private static final String NBT_RADIUS = "MagnetRadius";

    /** Скорость, с которой предметы летят к игроку (блоков за тик). */
    private static final double PULL_SPEED = 0.9;

    public MagnetWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flagIn) {
        tooltip.add(Component.translatable("item.clearitems.magnet_wand.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.clearitems.magnet_wand.tooltip.radius", getRadius(stack))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(WandUsage.tooltipDurability(stack));
        tooltip.add(Component.translatable("item.clearitems.magnet_wand.tooltip.hint")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        super.appendHoverText(stack, context, tooltip, flagIn);
    }

    // Полоска износа (как у инструментов) — только когда в конфиге включена прочность
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return WandUsage.isBarVisible(stack) || super.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return WandUsage.isBarVisible(stack) ? WandUsage.getBarWidth(stack) : super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return WandUsage.isBarVisible(stack) ? WandUsage.getBarColor(stack) : super.getBarColor(stack);
    }

    /** Читает текущий настроенный радиус из данных предмета (или дефолт). */
    public static int getRadius(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(NBT_RADIUS)) {
            return tag.getInt(NBT_RADIUS);
        }
        return DEFAULT_RADIUS;
    }

    private static void setRadius(ItemStack stack, int radius) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(NBT_RADIUS, radius));
    }

    private static int nextRadius(int current) {
        for (int i = 0; i < RADIUS_STEPS.length; i++) {
            if (RADIUS_STEPS[i] == current) {
                return RADIUS_STEPS[(i + 1) % RADIUS_STEPS.length];
            }
        }
        return RADIUS_STEPS[0];
    }

    /** Shift+ПКМ по воздуху — переключает радиус действия жезла. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                int newRadius = nextRadius(getRadius(stack));
                setRadius(stack, newRadius);
                player.displayClientMessage(
                        Component.literal("Радиус Жезла Магнита: " + newRadius + " блоков"),
                        true
                );
                level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
                        SoundSource.PLAYERS, 0.6F, 1.4F);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        double radius = getRadius(stack);

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(context.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }

        Vec3 center = Vec3.atCenterOf(clickedPos);
        ServerLevel serverLevel = (ServerLevel) level;

        AABB searchBox = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );

        List<ItemEntity> items = serverLevel.getEntitiesOfClass(ItemEntity.class, searchBox);

        Vec3 playerCenter = serverPlayer.position().add(0, serverPlayer.getBbHeight() / 2.0, 0);

        int pulled = 0;
        int skipped = 0;
        for (ItemEntity item : items) {
            // Точное расстояние — сфера, а не куб AABB
            if (item.position().distanceToSqr(center) > radius * radius) {
                continue;
            }
            // Защищённые whitelist-ом дропы не трогаем
            if (ClearItemsConfig.isWhitelisted(item.getItem())) {
                skipped++;
                continue;
            }
            // Скорость в сторону игрока — предметы «летят» к нему,
            // а подбор произойдёт ванильной логикой (со стеками и merge)
            Vec3 dir = playerCenter.subtract(item.position()).normalize().scale(PULL_SPEED);
            item.setDeltaMovement(dir);
            item.setDefaultPickUpDelay();
            // Синхронизация движения клиента — как у выброса из диспетсера
            item.hurtMarked = true;
            pulled++;
            spawnPullParticles(serverLevel, item.position());
        }

        if (pulled == 0) {
            if (skipped > 0) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "Жезл Магнита не нашёл предметов, кроме защищённых whitelist-ом: " + skipped));
            } else {
                serverPlayer.sendSystemMessage(Component.literal(
                        "Жезл Магнита не нашёл предметов в радиусе " + (int) radius + " блоков"));
            }
            return InteractionResult.PASS;
        }

        // Звук телепортации/притяжения в точке клика
        level.playSound(null, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.8F, 0.8F);

        // Расход прочности и кулдаун — только когда жезл реально что-то притянул
        if (WandUsage.durabilityEnabled() && !serverPlayer.isCreative()) {
            EquipmentSlot slot = context.getHand() == InteractionHand.OFF_HAND
                    ? EquipmentSlot.OFFHAND
                    : EquipmentSlot.MAINHAND;
            boolean broken = WandUsage.damageOnce(serverPlayer, stack, slot);
            if (broken) {
                serverPlayer.sendSystemMessage(Component.literal("Жезл Магнита сломался!"));
            } else {
                WandUsage.sendUsesLeft(serverPlayer,
                        ClearItemsConfig.getWandDurability() - WandUsage.getUses(stack),
                        ClearItemsConfig.getWandDurability());
            }
        }
        WandUsage.applyCooldown(serverPlayer, stack);

        String summary = "Жезл Магнита притянул предметов: " + pulled
                + " (радиус " + (int) radius + " блоков)";
        if (skipped > 0) {
            summary += " — пропущено по whitelist: " + skipped;
        }
        serverPlayer.sendSystemMessage(Component.literal(summary));

        return InteractionResult.CONSUME;
    }

    /** Искры на месте каждого притягиваемого предмета. */
    private static void spawnPullParticles(ServerLevel level, Vec3 pos) {
        level.sendParticles(
                ParticleTypes.END_ROD,
                pos.x, pos.y + 0.1, pos.z,
                3,
                0.15, 0.15, 0.15,
                0.02
        );
    }
}
