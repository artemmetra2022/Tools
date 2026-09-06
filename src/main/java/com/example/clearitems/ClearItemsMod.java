package com.example.clearitems;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(ClearItemsMod.MOD_ID)
public class ClearItemsMod {

    public static final String MOD_ID = "clearitems";

    public ClearItemsMod(IEventBus modEventBus, ModContainer modContainer) {
        // Регистрируем все предметы мода (в т.ч. Жезл Очистки) на mod event bus
        ModItems.register(modEventBus);
        // Добавляем предметы мода в подходящую вкладку творческого инвентаря
        modEventBus.addListener(ModCreativeTabs::onBuildCreativeTab);

        // Whitelist предметов, которые нельзя удалять с земли (server config)
        modContainer.registerConfig(ModConfig.Type.SERVER, ClearItemsConfig.SPEC);

        // Регистрируем обработчик события команд на общей шине событий игры
        NeoForge.EVENT_BUS.register(this);
        // Обработчик приветственного сообщения при входе игрока в мир
        NeoForge.EVENT_BUS.register(new ClearItemsWelcomeHandler());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ClearItemsCommand.register(event.getDispatcher());
        ClearItemsHelpCommand.register(event.getDispatcher());
    }
}
