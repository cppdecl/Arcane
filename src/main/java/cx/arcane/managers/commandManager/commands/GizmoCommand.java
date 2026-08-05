package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.gizmoManager.GizmoManager;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.TimeUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@NullMarked
public class GizmoCommand {

    // ================== Command Tree ==================

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(GizmoCommand::requirements)
                .then(Commands.literal("give")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(GizmoCommand::suggestPlayers)
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(GizmoCommand::suggestTools)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(GizmoCommand::handleGive)
                                                .then(Commands.argument("duration", StringArgumentType.word())
                                                        .executes(GizmoCommand::handleGive))
                                        )
                                )
                        )
                        .then(Commands.literal("all")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(GizmoCommand::suggestTools)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(GizmoCommand::handleGiveAll)
                                                .then(Commands.argument("duration", StringArgumentType.word())
                                                        .executes(GizmoCommand::handleGiveAll))
                                        )
                                )
                        )
                )
                .build();
    }

    // ================== Suggestions ==================

    private static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        String typed = builder.getRemaining().toLowerCase();
        for (Player p : PlayerManager.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(typed)) {
                builder.suggest(p.getName());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestTools(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        String typed = builder.getRemaining().toLowerCase();
        for (String tool : GizmoManager.getToolTypes()) {
            if (tool.toLowerCase().startsWith(typed)) {
                builder.suggest(tool);
            }
        }
        return builder.buildFuture();
    }

    // ================== Permissions ==================

    private static boolean requirements(CommandSourceStack stack) {
        CommandSender sender = stack.getExecutor();
        if (sender instanceof Player p) {
            return p.hasPermission("arcane.rank.management");
        }
        return true; // console allowed
    }

    // ================== Handlers ==================

    private static int handleGive(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getExecutor();

        String targetName = StringArgumentType.getString(ctx, "name");
        String type = StringArgumentType.getString(ctx, "type").toLowerCase(Locale.ROOT);
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sendError(sender, "That user does not exist or is offline!");
            return 0;
        }

        long durationMs = parseDurationOrDefault(ctx, defaultDurationFor(type));
        if (durationMs <= 0) return 0;

        ItemStack baseItem = createItem(type, durationMs);
        if (baseItem == null) {
            sendError(sender, "Invalid gizmo type.");
            return 0;
        }

        for (int i = 0; i < amount; i++) {
            ItemStack item = baseItem.clone();
            if (target.getInventory().firstEmpty() == -1) {
                target.getWorld().dropItem(target.getLocation(), item);
            } else {
                target.getInventory().addItem(item);
            }
        }

        send(sender, Component.text(
                "Gave " + target.getName() + " " + amount + "x " +
                        PlainTextComponentSerializer.plainText().serialize(baseItem.effectiveName()) + "!",
                Colors.HOT_PINK
        ));

        return 1;
    }

    private static int handleGiveAll(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getExecutor();

        String type = StringArgumentType.getString(ctx, "type").toLowerCase(Locale.ROOT);
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        long durationMs = parseDurationOrDefault(ctx, defaultDurationFor(type));
        if (durationMs <= 0) return 0;

        ItemStack baseItem = createItem(type, durationMs);
        if (baseItem == null) {
            sendError(sender, "Invalid gizmo type.");
            return 0;
        }

        int given = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < amount; i++) {
                ItemStack item = baseItem.clone();
                if (target.getInventory().firstEmpty() == -1) {
                    target.getWorld().dropItem(target.getLocation(), item);
                } else {
                    target.getInventory().addItem(item);
                }
            }
            given++;
        }

        if (given == 0) {
            send(sender, Component.text(
                    "No players online. Nothing was given.",
                    Colors.HOT_PINK
            ));
        } else {
            send(sender, Component.text(
                    "Gave " + given + " players " + amount + "x " +
                            PlainTextComponentSerializer.plainText().serialize(baseItem.effectiveName()) + "!",
                    Colors.HOT_PINK
            ));
        }

        return 1;
    }

    // ================== Utilities ==================

    private static ItemStack createItem(String type, long durationMs) {
        return switch (type) {
            case "trenchpickaxe" -> GizmoManager.TrenchPickaxe.createItem(durationMs);
            case "fireaxe" -> GizmoManager.FireAxe.createItem(durationMs);
            case "sellwand" -> GizmoManager.SellWand.createItem(durationMs);
            case "trenchshovel" -> GizmoManager.TrenchShovel.createItem(durationMs);
            default -> null;
        };
    }

    private static long parseDurationOrDefault(
            CommandContext<CommandSourceStack> ctx,
            long defaultMs
    ) {
        try {
            String input = StringArgumentType.getString(ctx, "duration");
            long ms = TimeUtils.parseDurationString(input);
            if (ms <= 0) {
                sendError(ctx.getSource().getExecutor(), "Invalid time format.");
                return -1;
            }
            return ms;
        } catch (IllegalArgumentException ignored) {
            return defaultMs;
        }
    }

    private static long defaultDurationFor(String type) {
        return switch (type) {
            case "fireaxe" -> GizmoManager.FireAxe.getDestructionTimeMs();
            case "sellwand" -> GizmoManager.SellWand.getDestructionTimeMs();
            case "trenchshovel" -> GizmoManager.TrenchShovel.getDestructionTimeMs();
            default -> GizmoManager.TrenchPickaxe.getDestructionTimeMs();
        };
    }

    private static void send(CommandSender sender, Component message) {
        if (sender == null) {
            Bukkit.getLogger().info(
                    PlainTextComponentSerializer.plainText().serialize(message)
            );
            return;
        }

        sender.sendMessage(message);

        if (sender instanceof Player p) {
            p.sendActionBar(message);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1, 1);
        }
    }

    private static void sendError(CommandSender sender, String message) {
        Component res = Component.text(message, TextColor.color(0xff0000));

        if (sender == null) {
            Bukkit.getLogger().warning(message);
            return;
        }

        sender.sendMessage(res);

        if (sender instanceof Player p) {
            p.sendActionBar(res);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        }
    }
}
