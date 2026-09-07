package com.example.clearitems;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Жезл Заморозки (Freeze Wand).
 *
 * Работает в два шага, чтобы не разбирать конструкции "вслепую":
 *  1) Первый ПКМ по блоку — находит ближайший контрапшн Create в радиусе
 *     поиска и подсвечивает его (эффект Glowing на несколько секунд) +
 *     ледяные частицы, ничего не разбирая. Игрок видит рамку контура
 *     сущности сквозь блоки — как обычный ванильный outline от Glowing.
 *  2) Повторный ПКМ по тому же (или ближайшему) контрапшну в течение
 *     нескольких секунд после подсветки — подтверждает и вызывает
 *     disassemble(), превращая его обратно в блоки.
 *
 * Если после подсветки прошло слишком много времени, следующий клик
 * снова только подсвечивает — так игрок не разберёт что-то по случайности.
 *
 * При подсветке игроку также показывается количество блоков в конструкции —
 * чтобы решение "разбирать или нет" принималось осознанно, а не вслепую.
 *
 * Радиус поиска настраивается по Shift+ПКМ в воздухе, аналогично Жезлу Очистки.
 * Если в этот момент есть активная подсветка — Shift+ПКМ вместо смены радиуса
 * снимает подсветку и отменяет ожидающую разборку (передумал — просто отмени).
 */
public class FreezeWandItem extends Item {

    private static final int[] RADIUS_STEPS = {8, 16, 32, 64};
    private static final int DEFAULT_RADIUS = 32;
    private static final String NBT_RADIUS = "FreezeRadius";

    // Время, в течение которого повторный клик считается подтверждением разборки
    private static final long CONFIRM_WINDOW_TICKS = 100; // 5 секунд

    public FreezeWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flagIn) {
        tooltip.add(Component.translatable("item.clearitems.freeze_wand.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.clearitems.freeze_wand.tooltip.radius", getRadius(stack))
                .withStyle(ChatFormatting.AQUA));

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID("FreezeTarget")) {
            // Точное время истечения окна подтверждения знает только сервер,
            // поэтому в тултипе — просто общий индикатор "возможно есть цель"
            tooltip.add(Component.translatable("item.clearitems.freeze_wand.tooltip.pending")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        }

        tooltip.add(WandUsage.tooltipDurability(stack));
        tooltip.add(Component.translatable("item.clearitems.freeze_wand.tooltip.hint")
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

    /**
     * Shift+ПКМ по воздуху.
     * Если есть активная ожидающая подтверждения подсветка — отменяет её
     * (снимает Glowing, сбрасывает NBT-цель), не трогая разборку.
     * Иначе — переключает радиус поиска, как раньше.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                if (hasActiveTarget(stack, level.getGameTime())) {
                    cancelPendingTarget(stack, level);
                    player.displayClientMessage(
                            Component.literal("Подсветка снята, разборка отменена."),
                            true
                    );
                    level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                            SoundSource.PLAYERS, 0.6F, 1.6F);
                } else {
                    int newRadius = nextRadius(getRadius(stack));
                    setRadius(stack, newRadius);
                    player.displayClientMessage(
                            Component.literal("Радиус поиска Жезла Заморозки: " + newRadius + " блоков"),
                            true
                    );
                    level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
                            SoundSource.PLAYERS, 0.6F, 1.4F);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        return InteractionResultHolder.pass(stack);
    }

    /** Есть ли у стака ещё не истёкшая ожидающая подтверждения цель. */
    private static boolean hasActiveTarget(ItemStack stack, long now) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID("FreezeTarget")
                && (now - tag.getLong("FreezeTargetTick")) <= CONFIRM_WINDOW_TICKS;
    }

    /** Снимает Glowing с подсвеченного контрапшна (если он ещё существует) и чистит данные цели. */
    private static void cancelPendingTarget(ItemStack stack, Level level) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID("FreezeTarget") && level instanceof ServerLevel serverLevel) {
            UUID targetId = tag.getUUID("FreezeTarget");
            Entity target = serverLevel.getEntity(targetId);
            if (target != null) {
                target.setGlowingTag(false);
            }
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.remove("FreezeTarget");
            t.remove("FreezeTargetTick");
        });
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        double radius = getRadius(stack);
        Vec3 center = Vec3.atCenterOf(clickedPos);
        AABB searchBox = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );

        List<AbstractContraptionEntity> contraptions = level.getEntitiesOfClass(
                AbstractContraptionEntity.class,
                searchBox
        );

        if (contraptions.isEmpty()) {
            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(
                        Component.literal("Рядом нет движущихся конструкций Create (радиус "
                                + (int) radius + " блоков)")
                );
            }
            return InteractionResult.FAIL;
        }

        AbstractContraptionEntity closest = contraptions.stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(center)))
                .orElse(null);

        if (closest == null) {
            return InteractionResult.FAIL;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        UUID targetId = closest.getUUID();
        long now = level.getGameTime();

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean hasPendingTarget = tag.hasUUID("FreezeTarget")
                && tag.getUUID("FreezeTarget").equals(targetId)
                && (now - tag.getLong("FreezeTargetTick")) <= CONFIRM_WINDOW_TICKS;

        if (!hasPendingTarget) {
            // ШАГ 1: подсветка, без разборки
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
                t.putUUID("FreezeTarget", targetId);
                t.putLong("FreezeTargetTick", now);
            });

            applyGlow(closest, (int) CONFIRM_WINDOW_TICKS);
            spawnFrostParticles(serverLevel, closest.position(), 30);

            level.playSound(null, clickedPos, SoundEvents.GLASS_BREAK,
                    SoundSource.PLAYERS, 0.5F, 1.8F);

            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                int blockCount = countBlocks(closest);
                String blockInfo = blockCount >= 0
                        ? " (" + blockCount + " " + blockWord(blockCount) + ")"
                        : "";
                serverPlayer.sendSystemMessage(
                        Component.literal("Конструкция подсвечена" + blockInfo
                                + ". Кликните ещё раз в течение 5 секунд, чтобы разобрать её, "
                                + "или Shift+ПКМ в воздухе — чтобы отменить.")
                );
            }
            return InteractionResult.CONSUME;
        }

        // Цель могла исчезнуть или "приземлиться" сама между первым и вторым кликом
        if (!closest.isAlive() || !closest.getUUID().equals(targetId)) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
                t.remove("FreezeTarget");
                t.remove("FreezeTargetTick");
            });
            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(
                        Component.literal("Подсвеченная конструкция больше недоступна — подсветка сброшена. Кликните ещё раз, чтобы выбрать заново.")
                );
            }
            return InteractionResult.FAIL;
        }

        // ШАГ 2: подтверждение — реальная разборка
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.remove("FreezeTarget");
            t.remove("FreezeTargetTick");
        });

        Vec3 dissolvePos = closest.position();
        spawnFrostParticles(serverLevel, dissolvePos, 60);
        level.playSound(null, clickedPos, SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 1.0F, 0.7F);

        closest.disassemble();

        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            // Прочность и кулдаун списываются только за реальную разборку,
            // подсветка (шаг 1) бесплатна
            if (WandUsage.durabilityEnabled() && !serverPlayer.isCreative()) {
                net.minecraft.world.entity.EquipmentSlot slot = context.getHand() == InteractionHand.OFF_HAND
                        ? net.minecraft.world.entity.EquipmentSlot.OFFHAND
                        : net.minecraft.world.entity.EquipmentSlot.MAINHAND;
                boolean broken = WandUsage.damageOnce(serverPlayer, stack, slot);
                if (broken) {
                    serverPlayer.sendSystemMessage(Component.literal(
                            "Жезл Заморозки сломался после разборки!"));
                } else {
                    WandUsage.sendUsesLeft(serverPlayer,
                            ClearItemsConfig.getWandDurability() - WandUsage.getUses(stack),
                            ClearItemsConfig.getWandDurability());
                }
            }
            WandUsage.applyCooldown(serverPlayer, stack);

            serverPlayer.sendSystemMessage(
                    Component.literal("Конструкция заморожена и разобрана в блоки!")
            );
        }

        return InteractionResult.CONSUME;
    }

    /**
     * Количество блоков в конструкции. Возвращает -1, если по какой-то причине
     * не удалось получить данные контрапшна (например, ещё не синхронизирован) —
     * тогда сообщение об этом просто не показываем, вместо падения с ошибкой.
     */
    private static int countBlocks(AbstractContraptionEntity entity) {
        try {
            var contraption = entity.getContraption();
            if (contraption == null) {
                return -1;
            }
            return contraption.getBlocks().size();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Русское склонение слова "блок" под число (1 блок, 2 блока, 5 блоков). */
    private static String blockWord(int count) {
        int n = Math.abs(count) % 100;
        int n1 = n % 10;
        if (n > 10 && n < 20) return "блоков";
        if (n1 == 1) return "блок";
        if (n1 >= 2 && n1 <= 4) return "блока";
        return "блоков";
    }

    /** Подсвечивает контрапшн ванильным эффектом Glowing на заданное число тиков. */
    private static void applyGlow(Entity entity, int durationTicks) {
        entity.setGlowingTag(true);
        // Снимаем подсветку по истечении окна подтверждения через отложенную задачу сервера
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                    (int) (serverLevel.getGameTime() + durationTicks),
                    () -> {
                        if (entity.isAlive()) {
                            entity.setGlowingTag(false);
                        }
                    }
            ));
        }
    }

    /** Ледяные искры вокруг конструкции. */
    private static void spawnFrostParticles(ServerLevel level, Vec3 center, int count) {
        level.sendParticles(
                ParticleTypes.SNOWFLAKE,
                center.x, center.y + 0.5, center.z,
                count,
                1.5, 1.0, 1.5,
                0.02
        );
        level.sendParticles(
                ParticleTypes.ITEM_SNOWBALL,
                center.x, center.y + 0.5, center.z,
                count / 4,
                1.0, 0.8, 1.0,
                0.01
        );
    }
}
