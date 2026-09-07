package com.example.clearitems;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.CreativeModeTabs;

/**
 * Добавляет предметы мода в существующие вкладки творческого инвентаря,
 * не создавая отдельную новую вкладку (проще для пользователя — предмет
 * находится там же, где обычные инструменты).
 */
public class ModCreativeTabs {

    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        // Кладём все жезлы мода во вкладку "Инструменты и утилиты"
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.WAND_OF_CLEARING);
            event.accept(ModItems.FREEZE_WAND);
            event.accept(ModItems.MAGNET_WAND);
        }
    }
}
