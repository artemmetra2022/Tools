package com.example.clearitems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * Жезл Очистки (Wand of Clearing).
 * При правом клике по любому блоку удаляет все предметы (ItemEntity),
 * находящиеся в радиусе от точки клика.
 *
 * Радиус настраивается циклически по Shift+ПКМ в воздухе и хранится
 * в NBT предмета (индивидуально для каждого стака), поэтому переживает
 * перезаход и переносится вместе с предметом.
 */
public class WandOfClearingItem extends Item {

    // Доступные значения радиуса для циклического переключения
    private static final int[] RADIUS_STEPS = {8, 16, 32, 50, 100};
    private static final int DEFAULT_RADIUS = 50;
    private static final String NBT_RADIUS = "ClearRadius";

    public WandOfClearingItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flagIn) {
        tooltip.add(Component.translatable("item.clearitems.wand_of_clearing.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.clearitems.wand_of_clearing.tooltip.radius", getRadius(stack))
                .withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable("item.clearitems.wand_of_clearing.tooltip.hint")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        super.appendHoverText(stack, context, tooltip, flagIn);
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
        // текущее значение не входит в список шагов (например, старый предмет) — начинаем сначала
        return RADIUS_STEPS[0];
    }

    /** Shift+ПКМ по воздуху — переключает радиус действия жезла. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                int newRadius = nextRadius(getRadius(stack));
                setRadius(stack, newRadius);
                player.displayClientMessage(
                        Component.literal("Радиус Жезла Очистки: " + newRadius + " блоков"),
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

        // Логика удаления выполняется только на сервере (авторитативная сторона),
        // чтобы не было рассинхрона клиент/сервер и дублирования эффектов.
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Vec3 center = Vec3.atCenterOf(clickedPos);

        AABB searchBox = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchBox);

        int removed = 0;
        for (ItemEntity item : items) {
            // Дополнительно проверяем точное расстояние (AABB — куб, а нам нужна сфера)
            if (item.position().distanceToSqr(center) <= radius * radius) {
                // Зелёное свечение на месте каждого удаляемого предмета
                if (level instanceof ServerLevel serverLevel) {
                    spawnClearParticles(serverLevel, item.position());
                }
                item.discard();
                removed++;
            }
        }

        // Кольцо частиц по границе радиуса — наглядно показывает зону действия
        if (level instanceof ServerLevel serverLevel) {
            spawnRadiusRing(serverLevel, center, radius);
        }

        // Звук и сообщение игроку, если это игрок
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            int finalRemoved = removed;
            serverPlayer.sendSystemMessage(
                    Component.literal("Жезл Очистки удалил предметов: " + finalRemoved + " (радиус " + (int) radius + " блоков)")
            );
        }

        level.playSound(
                null,
                clickedPos,
                SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS,
                1.0F,
                1.2F
        );

        return InteractionResult.CONSUME;
    }

    /** Зелёное свечение (частицы) в точке, где был удалён предмет. */
    private static void spawnClearParticles(ServerLevel level, Vec3 pos) {
        level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                pos.x, pos.y + 0.1, pos.z,
                6,          // количество частиц
                0.2, 0.2, 0.2,
                0.01
        );
        level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.COMPOSTER, // мягкое зелёное свечение
                pos.x, pos.y + 0.1, pos.z,
                4,
                0.15, 0.15, 0.15,
                0.0
        );
    }

    /** Кольцо частиц по границе радиуса действия жезла (визуализация зоны). */
    private static void spawnRadiusRing(ServerLevel level, Vec3 center, double radius) {
        int points = 48;
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double x = center.x + radius * Math.cos(angle);
            double z = center.z + radius * Math.sin(angle);
            level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    x, center.y, z,
                    1,
                    0, 0, 0,
                    0.0
            );
        }
    }
}
