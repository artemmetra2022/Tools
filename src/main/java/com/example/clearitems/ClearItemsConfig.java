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
}
