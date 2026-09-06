package com.example.clearitems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Показывает игроку краткое описание мода при первом входе в мир на сессии
 * (логин, а не каждый респавн) — чтобы новый игрок сразу знал про жезлы
 * и команду /clearitemshelp, не читая README.
 */
public class ClearItemsWelcomeHandler {

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        var player = event.getEntity();

        player.sendSystemMessage(
                Component.literal("ClearItems: ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("Жезл Очистки убирает предметы с земли, Жезл Заморозки разбирает конструкции Create.")
                                .withStyle(ChatFormatting.GRAY))
        );
        player.sendSystemMessage(
                Component.literal("Подробности: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("/clearitemshelp").withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE))
        );
    }
}
