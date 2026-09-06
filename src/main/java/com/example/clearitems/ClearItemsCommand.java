package com.example.clearitems;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Команда /clear1 — удаляет предметы (ItemEntity), лежащие на земле.
 * Без аргументов — во всех загруженных измерениях сервера (эквивалент /kill @e[type=item]).
 * С аргументом /clear1 radius <N> — только в радиусе N блоков вокруг вызвавшего,
 * в его текущем измерении (сферой, не кубом).
 */
public class ClearItemsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("clear1")
                        // требуем права оператора (уровень 2, как у большинства игровых команд)
                        .requires(source -> source.hasPermission(2))
                        .executes(ClearItemsCommand::execute)
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                .executes(ClearItemsCommand::executeWithRadius))
        );
    }

    private static int execute(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int totalRemoved = 0;
        int skipped = 0;

        // Проходим по всем измерениям сервера (Overworld, Nether, End и кастомные)
        for (ServerLevel level : source.getServer().getAllLevels()) {
            List<ItemEntity> items = level.getEntitiesOfClass(
                    ItemEntity.class,
                    // Бесконечный (очень большой) AABB, чтобы захватить все предметы в мире
                    AABB.INFINITE
            );

            for (ItemEntity item : items) {
                // Предметы из whitelist конфига не трогаем
                if (ClearItemsConfig.isWhitelisted(item.getItem())) {
                    skipped++;
                    continue;
                }
                item.discard(); // корректное удаление сущности с сервера
                totalRemoved++;
            }
        }

        int finalCount = totalRemoved;
        int finalSkipped = skipped;
        source.sendSuccess(
                () -> Component.literal("Удалено предметов с земли: " + finalCount
                        + (finalSkipped > 0 ? " (пропущено по whitelist: " + finalSkipped + ")" : "")),
                true
        );

        return totalRemoved;
    }

    /** /clear1 radius <N> — удаляет предметы в радиусе N блоков вокруг вызвавшего, в его измерении. */
    private static int executeWithRadius(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int radius = IntegerArgumentType.getInteger(context, "radius");
        Vec3 center = source.getPosition();
        ServerLevel level = source.getLevel();

        AABB searchBox = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchBox);

        int removed = 0;
        int skipped = 0;
        for (ItemEntity item : items) {
            // AABB — куб, а нам нужна сфера: проверяем точное расстояние
            if (item.position().distanceToSqr(center) <= (double) radius * radius) {
                // Предметы из whitelist конфига не трогаем
                if (ClearItemsConfig.isWhitelisted(item.getItem())) {
                    skipped++;
                    continue;
                }
                item.discard();
                removed++;
            }
        }

        int finalCount = removed;
        int finalSkipped = skipped;
        source.sendSuccess(
                () -> Component.literal("Удалено предметов с земли (радиус " + radius + "): " + finalCount
                        + (finalSkipped > 0 ? " (пропущено по whitelist: " + finalSkipped + ")" : "")),
                true
        );

        return removed;
    }
}
