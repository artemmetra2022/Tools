package com.example.clearitems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Конфигурация мода (тип SERVER: файл создаётся в <мир>/serverconfig/clearitems-server.toml).
 *
 * Whitelist — список ID предметов, которые /clear1 (в любом виде) и Жезл Очистки
 * никогда не удалят с земли. Удобно, чтобы случайно не вычистить редкий дроп
 * (например, незеритовые звёзды с шёлка или несгораемые предметы).
 */
public class ClearItemsConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELISTED_ITEMS;

    public static final ModConfigSpec.IntValue WAND_DURABILITY;
    public static final ModConfigSpec.IntValue WAND_COOLDOWN_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("ClearItems server config")
                .push("items");
        WHITELISTED_ITEMS = builder
                .comment("Items that /clear1 and the Wand of Clearing never remove from the ground.",
                        "Format: \"modid:item_id\", one per line. Example: \"minecraft:nether_star\"")
                .defineListAllowEmpty("whitelist",
                        List.of("minecraft:nether_star"),
                        o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
        builder.pop();

        builder.comment("Durability and cooldown of all wands")
                .push("wands");
        WAND_DURABILITY = builder
                .comment("How many activations a wand survives before breaking. 0 = wands never break.",
                        "Does not apply to creative mode players.",
                        "Applies to: Wand of Clearing (only when it actually removes items),",
                        "Freeze Wand (only the disassemble step), Magnet Wand (only when it pulls items).")
                .defineInRange("durability", 256, 0, Integer.MAX_VALUE);
        WAND_COOLDOWN_TICKS = builder
                .comment("Cooldown in ticks (20 ticks = 1 second) after a wand activation.",
                        "Protects against right-click spam (especially the Magnet Wand). 0 = no cooldown.",
                        "Freeze Wand pays the cooldown only on the disassemble step, not on highlighting.")
                .defineInRange("cooldown_ticks", 40, 0, 36000);
        builder.pop();

        SPEC = builder.build();
    }

    /** Находится ли этот стак в whitelist (его нельзя удалять с земли). */
    public static boolean isWhitelisted(ItemStack stack) {
        if (!SPEC.isLoaded()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String entry : WHITELISTED_ITEMS.get()) {
            if (entry.equals(id.toString())) {
                return true;
            }
        }
        return false;
    }

    /** Максимальное число использований жезла до поломки (0 = бесконечные жезлы). */
    public static int getWandDurability() {
        return SPEC.isLoaded() ? WAND_DURABILITY.get() : 0;
    }

    /** Кулдаун жезлов в тиках после активации (0 = без кулдауна). */
    public static int getWandCooldownTicks() {
        return SPEC.isLoaded() ? WAND_COOLDOWN_TICKS.get() : 0;
    }
}
