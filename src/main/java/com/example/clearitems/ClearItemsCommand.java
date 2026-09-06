package com.example.clearitems;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.List;

/**
 * Команда /clear1 — удаляет все предметы (ItemEntity), лежащие на земле,
 * во всех загруженных измерениях сервера. Эквивалент /kill @e[type=item].
 */
public class ClearItemsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("clear1")
                        // требуем права оператора (уровень 2, как у большинства игровых команд)
                        .requires(source -> source.hasPermission(2))
                        .executes(ClearItemsCommand::execute)
        );
    }

    private static int execute(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int totalRemoved = 0;

        // Проходим по всем измерениям сервера (Overworld, Nether, End и кастомные)
        for (ServerLevel level : source.getServer().getAllLevels()) {
            List<ItemEntity> items = level.getEntitiesOfClass(
                    ItemEntity.class,
                    // Бесконечный (очень большой) AABB, чтобы захватить все предметы в мире
                    net.minecraft.world.phys.AABB.INFINITE
            );

            for (ItemEntity item : items) {
                item.discard(); // корректное удаление сущности с сервера
                totalRemoved++;
            }
        }

        int finalCount = totalRemoved;
        source.sendSuccess(
                () -> Component.literal("Удалено предметов с земли: " + finalCount),
                true
        );

        return totalRemoved;
    }
}
