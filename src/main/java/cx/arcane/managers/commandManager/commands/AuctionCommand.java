package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.auctionManager.AuctionGui;
import cx.arcane.managers.auctionManager.AuctionManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.ItemUtils;
import cx.arcane.utils.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;

public final class AuctionCommand {

    private AuctionCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .executes(AuctionCommand::handleBrowse)
                .then(Commands.argument("item", StringArgumentType.greedyString())
                        .suggests(AuctionCommand::suggestItems)
                        .executes(AuctionCommand::handleSearch))
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", StringArgumentType.greedyString())
                                .executes(AuctionCommand::handleSell)))
                .then(Commands.literal("qsell")
                        .requires(src -> src.getExecutor() instanceof Player pl && (pl.hasPermission("arcane.rank.management")))
                        .then(Commands.argument("price", StringArgumentType.greedyString())
                                .executes(AuctionCommand::handleQuickSell)))
                .then(Commands.literal("history")
                        .requires(src -> src.getExecutor() instanceof Player pl && (pl.hasPermission("arcane.rank.management")))
                        .then(Commands.argument("player", StringArgumentType.greedyString())
                                .suggests(AuctionCommand::suggestPlayers)
                                .executes(AuctionCommand::handleHistory)))
                .build();
    }

    private static int handleBrowse(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);
        AuctionGui.openBrowse(p, "");
        return 1;
    }

    private static int handleSearch(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);
        AuctionGui.openBrowse(p, StringArgumentType.getString(ctx, "item"));
        return 1;
    }

    private static int handleSell(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            AuctionManager.sendError(p, "Hold an item to sell!");
            return 0;
        }

        long price = parsePrice(ctx, "price");
        if (price <= 0) {
            AuctionManager.sendError(p, "Price must be at least $1!");
            return 0;
        }

        if (!AuctionManager.canSell(p, hand, price)) return 0;

        AuctionGui.openSellConfirm(p, hand, price, true);
        return 1;
    }

    private static int handleQuickSell(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            AuctionManager.sendError(p, "Hold an item to sell!");
            return 0;
        }

        long price = parsePrice(ctx, "price");
        if (price <= 0) {
            AuctionManager.sendError(p, "Price must be at least $1!");
            return 0;
        }

        if (!AuctionManager.canSell(p, hand, price)) return 0;

        AuctionManager.executeSell(p, hand, price, true);
        return 1;
    }

    private static int handleHistory(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player p)) return 0;

        String name = StringArgumentType.getString(ctx, "player");
        PlayerData target = PlayerManager.getByName(name);
        if (target == null) {
            AuctionManager.sendError(p, "Player not found!");
            return 0;
        }

        AuctionGui.openTransactions(p, target.getUniqueId(), "");
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

    private static long parsePrice(CommandContext<CommandSourceStack> ctx, String argName) {
        String raw = StringArgumentType.getString(ctx, argName);
        if (raw.isBlank()) return 0;
        double parsed = Text.parseAmountString(raw);
        if (parsed <= 0) return 0;
        return (long) parsed;
    }
}