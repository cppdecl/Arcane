package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.managers.coinflipManager.CoinFlipManager;
import cx.arcane.managers.coinflipManager.CoinFlipWager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Text;
import dev.triumphteam.gui.element.GuiItem;
import dev.triumphteam.gui.layout.GuiLayout;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import dev.triumphteam.gui.slot.Slot;
import dev.triumphteam.gui.state.pagination.PagerState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CoinFlipCommand {

    public static LiteralCommandNode<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .executes(CoinFlipCommand::handleGui)
                .then(Commands.literal("cancel")
                        .executes(CoinFlipCommand::handleCancel))
                .then(Commands.literal("accept")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(CoinFlipCommand::suggestWagers)
                                .executes(CoinFlipCommand::handleAccept)))
                .then(Commands.argument("amount", StringArgumentType.greedyString())
                        .executes(CoinFlipCommand::handleWager))
                .build();
    }

    private static Player sender(CommandContext<CommandSourceStack> ctx) {
        return (Player) ctx.getSource().getExecutor();
    }

    private static int handleGui(CommandContext<CommandSourceStack> ctx) {
        openCoinFlipGui(sender(ctx));
        return 1;
    }

    static void openCoinFlipGui(Player p) {
        // snapshot the wager list on the calling thread — safe read from ConcurrentHashMap
        List<CoinFlipWager> wagers = List.copyOf(CoinFlipManager.getActiveWagers());

        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);

        int maxContentRows = 4;
        int itemsPerPage   = maxContentRows * 7;
        boolean paginated  = wagers.size() > itemsPerPage;

        int contentRows = paginated
                ? maxContentRows
                : Math.max(1, (int) Math.ceil(wagers.size() / 7.0));

        int rows       = Math.min(Math.max(contentRows + 2, 3), 6);
        int totalPages = Math.max(1, (wagers.size() + itemsPerPage - 1) / itemsPerPage);

        Gui.of(rows)
                .title(Text.toSmallCapsComponent("CoinFlip"))
                .spamPreventionDuration(110)
                .component(component -> {

                    PagerState<CoinFlipWager> pageState = null;

                    if (paginated) {
                        pageState = PagerState.of(
                                wagers,
                                GuiLayout.box(Slot.of(2, 2), Slot.of(rows - 1, 8))
                        );
                        component.remember(pageState);
                    }

                    PagerState<CoinFlipWager> finalPageState = pageState;

                    component.render(con -> {
                        if (paginated) {
                            finalPageState.forEach(entry -> con.setItem(entry.slot(), buildSkull(entry.element())));
                            int page = finalPageState.getCurrentPage();
                            if (page > 1)          con.setItem(rows, 1, prevButton(p, finalPageState));
                            if (page < totalPages) con.setItem(rows, 9, nextButton(p, finalPageState));
                        } else {
                            int index = 0;
                            for (CoinFlipWager wager : wagers) {
                                int col = (index % 7) + 2;
                                int row = (index / 7) + 2;
                                con.setItem(row, col, buildSkull(wager));
                                index++;
                            }
                        }

                        con.setItem(rows, 4, statsButton(p));
                        con.setItem(rows, 5, refreshButton(p));
                        con.setItem(rows, 6, wagerActionButton(p));
                    });
                })
                .build()
                .open(p);
    }

    private static GuiItem<Player, ItemStack> buildSkull(CoinFlipWager wager) {
        PlayerData owner = PlayerManager.getByUniqueId(wager.ownerId());
        if (owner == null) return ItemBuilder.from(Material.SKELETON_SKULL)
                .name(Component.text("Unknown", Colors.GRAY).decoration(TextDecoration.ITALIC, false))
                .asGuiItem();

        return ItemBuilder.skull()
                .owner(Bukkit.getOfflinePlayer(owner.getUniqueId()))
                .name(Component.text(owner.getUsername(), Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Wager: ", Colors.WHITE)
                        .append(Component.text(Text.formatShortBalanceWithSign("$", wager.amount()), Colors.HOT_PINK))
                        .decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    // runs on the clicking player's region thread — safe
                    if (CoinFlipManager.acceptWager(player, owner.getUniqueId())) {
                        ctx.guiView().close();
                    }
                });
    }

    private static GuiItem<Player, ItemStack> statsButton(Player p) {
        return ItemBuilder.from(Material.KNOWLEDGE_BOOK)
                .name(Text.toSmallCapsComponent("Statistics").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(
                        Component.text(" • Total Wins: ", Colors.WHITE).append(Component.text("0", Colors.HOT_PINK)).decoration(TextDecoration.ITALIC, false),
                        Component.text(" • Total Loss: ", Colors.WHITE).append(Component.text("0", Colors.HOT_PINK)).decoration(TextDecoration.ITALIC, false),
                        Component.text(" • WL Ratio: ", Colors.WHITE).append(Component.text("0.0", Colors.HOT_PINK)).decoration(TextDecoration.ITALIC, false)
                )
                .asGuiItem((player, ctx) -> p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 2f));
    }

    private static GuiItem<Player, ItemStack> refreshButton(Player p) {
        return ItemBuilder.from(Material.ANVIL)
                .name(Text.toSmallCapsComponent("Refresh").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to refresh wagers", Colors.WHITE).decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    ctx.guiView().close();
                    openCoinFlipGui(player);
                });
    }

    private static GuiItem<Player, ItemStack> wagerActionButton(Player p) {
        boolean hasWager = CoinFlipManager.playerHasWager(p.getUniqueId());
        return ItemBuilder.from(Material.PAPER)
                .name((hasWager
                        ? Text.toSmallCapsComponent("Cancel Wager").color(Colors.DARK_PINK)
                        : Text.toSmallCapsComponent("Create Wager").color(Colors.HOT_PINK))
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text(hasWager ? "Click to cancel your wager" : "Click to create a wager", Colors.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    // TODO: implement create/cancel flow from GUI
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 2f);
                });
    }

    private static GuiItem<Player, ItemStack> prevButton(Player p, PagerState<CoinFlipWager> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Previous").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to go to previous page", Colors.WHITE).decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                    state.prev();
                });
    }

    private static GuiItem<Player, ItemStack> nextButton(Player p, PagerState<CoinFlipWager> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Next").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to go to next page", Colors.WHITE).decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                    state.next();
                });
    }

    private static int handleWager(CommandContext<CommandSourceStack> ctx) {
        Player p = sender(ctx);
        String raw = StringArgumentType.getString(ctx, "amount");
        long amount;
        try {
            amount = Text.parseAmountString(raw);
        } catch (Exception e) {
            return err(p, "That amount is invalid.");
        }
        CoinFlipManager.createWager(p, amount);
        return 1;
    }

    private static int handleAccept(CommandContext<CommandSourceStack> ctx) {
        Player p = sender(ctx);
        String targetName = StringArgumentType.getString(ctx, "player");
        PlayerData target = PlayerManager.getByNameIgnoreCase(targetName);
        if (target == null) return err(p, "That player does not exist!");
        CoinFlipManager.acceptWager(p, target.getUniqueId());
        return 1;
    }

    private static int handleCancel(CommandContext<CommandSourceStack> ctx) {
        Player p = sender(ctx);
        CoinFlipManager.cancelWager(p);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestWagers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String input = builder.getRemainingLowerCase();
        for (CoinFlipWager wager : CoinFlipManager.getActiveWagers()) {
            PlayerData owner = PlayerManager.getByUniqueId(wager.ownerId());
            if (owner == null) continue;
            if (owner.getUsername().toLowerCase().startsWith(input)) builder.suggest(owner.getUsername());
        }
        return builder.buildFuture();
    }

    private static int err(Player p, String message) {
        Component msg = Component.text(message, Colors.DARK_PINK);
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        return 0;
    }
}