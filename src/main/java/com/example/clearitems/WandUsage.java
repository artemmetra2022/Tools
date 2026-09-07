package com.example.clearitems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Общая логика расхода жезлов: настраиваемая прочность и кулдаун.
 *
 * Прочность сделана намеренно БЕЗ ванильного MAX_DAMAGE-компонента:
 * ванильная прочность зашивается в предмет при регистрации и не может
 * включаться/выключаться серверным конфигом на лету. Вместо этого
 * счётчик использований хранится в CustomData, полоска износа рисуется
 * через isBarVisible/getBarWidth/getBarColor у самого предмета, а в
 * момент поломки воспроизводится ванильное поведение (звук, анимация
 * поломки, исчезновение стака) через LivingEntity.onEquippedItemBroken.
 */
public final class WandUsage {

    /** Ключ в CustomData: сколько использований жезл уже пережил. */
    private static final String NBT_USES = "WandUses";

    private WandUsage() {
    }

    /** Включена ли поломка жезлов в конфиге. */
    public static boolean durabilityEnabled() {
        return ClearItemsConfig.getWandDurability() > 0;
    }

    /** Показывать ли полоску износа (только если жезл уже изнашивался). */
    public static boolean isBarVisible(ItemStack stack) {
        return durabilityEnabled() && getUses(stack) > 0;
    }

    /** Доля оставшейся прочности: 1.0 — новый жезл, 0.0 — израсходован. */
    public static float getBarFraction(ItemStack stack) {
        int max = ClearItemsConfig.getWandDurability();
        if (max <= 0) {
            return 1.0F;
        }
        return 1.0F - (float) Math.min(getUses(stack), max) / max;
    }

    /** Ширина полоски износа (13 — целая, как у ванильных инструментов). */
    public static int getBarWidth(ItemStack stack) {
        return Math.round(getBarFraction(stack) * 13.0F);
    }

    /** Цвет полоски: от зелёного к красному по мере износа (как у ванильных инструментов). */
    public static int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(Math.max(0.0F, getBarFraction(stack)) / 3.0F, 1.0F, 1.0F);
    }

    /** Сколько использований жезл уже пережил (по данным самого стака). */
    public static int getUses(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(NBT_USES) ? Math.max(0, tag.getInt(NBT_USES)) : 0;
    }

    /**
     * Применяет один акт износа к жезлу. Возвращает true, если жезл сломался
     * (стак удалён, звук и анимация поломки проиграны).
     *
     * Вызывается ТОЛЬКО на сервере и только когда жезл реально сработал
     * (что-то удалил/притянул/разобрал), а не на каждый клик.
     * В креативе жезлы не изнашиваются (как ванильные инструменты).
     */
    public static boolean damageOnce(ServerPlayer player, ItemStack stack, EquipmentSlot slot) {
        int max = ClearItemsConfig.getWandDurability();
        if (max <= 0 || player.isCreative()) {
            return false;
        }

        int uses = getUses(stack);
        if (uses + 1 >= max) {
            // Ванильное поведение поломки: звук + анимация + исчезновение стака
            player.onEquippedItemBroken(stack.getItem(), slot);
            stack.shrink(1);
            return true;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(NBT_USES, uses + 1));
        return false;
    }

    /**
     * Запускает кулдаун жезла, если он включён в конфиге (0 = выкл).
     * Кулдаун общий на тип предмета — как у ванильного жемчуга Края.
     */
    public static void applyCooldown(ServerPlayer player, ItemStack stack) {
        int cooldown = ClearItemsConfig.getWandCooldownTicks();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(stack.getItem(), cooldown);
        }
    }

    /** Сообщение «сколько осталось использований» для игрока. */
    public static void sendUsesLeft(ServerPlayer player, int usesLeft, int max) {
        player.sendSystemMessage(Component.translatable(
                "message.clearitems.wand.uses_left",
                usesLeft, max
        ).withStyle(ChatFormatting.GRAY));
    }

    /** Строка тултипа с остатком прочности (для appendHoverText жезлов). */
    public static Component tooltipDurability(ItemStack stack) {
        int max = ClearItemsConfig.getWandDurability();
        if (max <= 0) {
            return Component.translatable("message.clearitems.wand.unbreakable")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        }
        return Component.translatable(
                "message.clearitems.wand.durability",
                Math.max(0, max - getUses(stack)), max
        ).withStyle(ChatFormatting.DARK_GRAY);
    }
}
