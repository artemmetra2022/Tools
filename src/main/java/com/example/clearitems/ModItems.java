package com.example.clearitems;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Централизованная регистрация всех предметов мода.
 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ClearItemsMod.MOD_ID);

    // Жезл Очистки — по ПКМ по блоку удаляет все ItemEntity в радиусе 50 блоков
    public static final DeferredItem<WandOfClearingItem> WAND_OF_CLEARING =
            ITEMS.registerItem(
                    "wand_of_clearing",
                    WandOfClearingItem::new,
                    new Item.Properties()
                            .stacksTo(1) // жезл не стакается, как обычный инструмент
            );

    // Жезл Заморозки — по ПКМ по блоку разбирает ближайшую конструкцию
    // (contraption) мода Create обратно в настоящие блоки в мире
    public static final DeferredItem<FreezeWandItem> FREEZE_WAND =
            ITEMS.registerItem(
                    "freeze_wand",
                    FreezeWandItem::new,
                    new Item.Properties()
                            .stacksTo(1)
            );

    // Жезл Магнита — по ПКМ по блоку притягивает все лежащие предметы
    // в радиусе к игроку (подбор — ванильный, со стеками и merge)
    public static final DeferredItem<MagnetWandItem> MAGNET_WAND =
            ITEMS.registerItem(
                    "magnet_wand",
                    MagnetWandItem::new,
                    new Item.Properties()
                            .stacksTo(1)
            );

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
