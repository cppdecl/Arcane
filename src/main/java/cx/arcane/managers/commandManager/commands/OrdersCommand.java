package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.ordersManager.OrdersGui;
import cx.arcane.managers.ordersManager.OrdersManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.ItemUtils;
import cx.arcane.utils.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public final class OrdersCommand {

    private OrdersCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .executes(OrdersCommand::handleBrowse)
                .then(Commands.argument("item", StringArgumentType.greedyString())
                        .suggests(OrdersCommand::suggestItems)
                        .executes(OrdersCommand::handleSearch))
                .then(Commands.literal("history")
                        .requires(src -> src.getExecutor() instanceof Player pl && pl.hasPermission("arcane.rank.management"))
                        .then(Commands.argument("player", StringArgumentType.greedyString())
                                .suggests(OrdersCommand::suggestPlayers)
                                .executes(OrdersCommand::handleHistory)))
                .build();
    }

    private static int handleBrowse(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;
        p.playSound(p, Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);
        OrdersGui.openBrowse(p, "");
        return 1;
    }

    private static int handleSearch(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;
        p.playSound(p, Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);
        OrdersGui.openBrowse(p, StringArgumentType.getString(ctx, "item"));
        return 1;
    }

    private static int handleHistory(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        String name = StringArgumentType.getString(ctx, "player");
        PlayerData target = PlayerManager.getByName(name);
        if (target == null) {
            OrdersManager.sendError(p, "Player not found!");
            return 0;
        }

        OrdersGui.openTransactions(p, target.getUniqueId(), "");
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestItems(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String partial = builder.getRemaining().toLowerCase();
        if (partial.isEmpty()) {
            ItemUtils.getAllEffectiveNames().forEach(builder::suggest);
        } else {
            ItemUtils.searchByName(partial).stream()
                    .map(ItemUtils::getEffectiveName)
                    .filter(n -> n.toLowerCase().contains(partial))
                    .forEach(builder::suggest);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase();
        PlayerManager.getAll().stream()
                .filter(n -> n.getUsername().toLowerCase().startsWith(typed))
                .forEach(a -> builder.suggest(a.getUsername()));
        return builder.buildFuture();
    }
}