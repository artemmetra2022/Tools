package com.example.clearitems;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Команда /clearitemshelp — краткая справка по всем возможностям мода.
 * Доступна всем игрокам (без прав оператора), в отличие от /clear1,
 * который требует уровень доступа 2.
 */
public class ClearItemsHelpCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("clearitemshelp")
                        .executes(ClearItemsHelpCommand::execute)
        );
    }

    private static int execute(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        send(source, "=== ClearItems: справка ===", ChatFormatting.GOLD, true);

        send(source, "Жезл Очистки", ChatFormatting.GREEN, true);
        send(source, "  ПКМ по блоку — удаляет все предметы на земле в радиусе.", ChatFormatting.GRAY, false);
        send(source, "  Shift+ПКМ в воздухе — переключить радиус действия.", ChatFormatting.GRAY, false);

        send(source, "Жезл Заморозки", ChatFormatting.AQUA, true);
        send(source, "  ПКМ по блоку — подсвечивает ближайшую конструкцию Create.", ChatFormatting.GRAY, false);
        send(source, "  Повторный ПКМ (в течение 5 сек) — разбирает её в блоки.", ChatFormatting.GRAY, false);
        send(source, "  Shift+ПКМ в воздухе — переключить радиус поиска, либо", ChatFormatting.GRAY, false);
        send(source, "  отменить текущую подсветку, если она активна.", ChatFormatting.GRAY, false);

        send(source, "Команды", ChatFormatting.YELLOW, true);
        send(source, "  /clear1 — удалить все предметы на земле во всём мире (требует прав оператора).", ChatFormatting.GRAY, false);
        send(source, "  /clearitemshelp — показать эту справку.", ChatFormatting.GRAY, false);

        return 1;
    }

    private static void send(CommandSourceStack source, String text, ChatFormatting color, boolean bold) {
        Component component = bold
                ? Component.literal(text).withStyle(color, ChatFormatting.BOLD)
                : Component.literal(text).withStyle(color);
        source.sendSuccess(() -> component, false);
    }
}
