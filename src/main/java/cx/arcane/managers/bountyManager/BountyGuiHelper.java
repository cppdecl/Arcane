package cx.arcane.managers.bountyManager;

import cx.arcane.Arcane;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.SignGUI;
import cx.arcane.utils.Text;
import dev.triumphteam.gui.layout.GuiLayout;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import dev.triumphteam.gui.slot.Slot;
import dev.triumphteam.gui.state.pagination.PagerState;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public final class BountyGuiHelper {

    private BountyGuiHelper() {}

    private static final Map<BountyListingSortType, Comparator<Bounty>> COMPARATORS = Map.of(
            BountyListingSortType.HIGHEST_PRICE, Comparator.comparingLong(Bounty::getReward).reversed(),
            BountyListingSortType.LOWEST_PRICE,  Comparator.comparingLong(Bounty::getReward)
    );

    private static final class BrowseState {
        BountyListingSortType sort = BountyListingSortType.HIGHEST_PRICE;
        String query               = "";
    }

    private static final java.util.concurrent.ConcurrentHashMap<UUID, BrowseState> BROWSE_STATES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static void schedule(@NotNull Player p, @NotNull Runnable task) {
        FoliaScheduler.getEntityScheduler().execute(p, Arcane.getPlugin(), task, null, 1L);
    }

    private static List<Bounty> filter(@NotNull List<Bounty> all, @NotNull BrowseState state) {
        String q = state.query.toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(b -> {
                    String name = PlayerManager.getName(b.getUniqueId());
                    return name != null && name.toLowerCase(Locale.ROOT).startsWith(q);
                })
                .sorted(COMPARATORS.get(state.sort))
                .toList();
    }

    private static dev.triumphteam.gui.element.GuiItem<Player, org.bukkit.inventory.ItemStack> buildBountyItem(@NotNull Bounty bounty) {
        String playerName = PlayerManager.getName(bounty.getUniqueId());
        if (playerName == null || playerName.isBlank()) playerName = "N/A";

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Bounty: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("$" + Text.formatShortBalance(bounty.getReward()), Colors.HOT_PINK)));
        lore.add(Component.text("Rank: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("#" + BountyManager.getBountyPosition(bounty.getUniqueId()), Colors.HOT_PINK)));

        return ItemBuilder.skull()
                .owner(Bukkit.getOfflinePlayer(bounty.getUniqueId()))
                .name(Component.text(playerName, Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .asGuiItem();
    }

    private static dev.triumphteam.gui.element.GuiItem<Player, org.bukkit.inventory.ItemStack> prevButton(
            @NotNull Player p, @NotNull PagerState<?> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Previous").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Go to previous page", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                    state.prev();
                });
    }

    private static dev.triumphteam.gui.element.GuiItem<Player, org.bukkit.inventory.ItemStack> nextButton(
            @NotNull Player p, @NotNull PagerState<?> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Next").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Go to next page", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                    state.next();
                });
    }

    public static void open(@NotNull Player p) {
        open(p, "");
    }

    public static void open(@NotNull Player p, @NotNull String query) {
        BrowseState state = BROWSE_STATES.computeIfAbsent(p.getUniqueId(), k -> new BrowseState());
        state.query = query;
        buildGui(p, state, BountyManager.getBounties());
    }

    private static void buildGui(@NotNull Player p, @NotNull BrowseState state, @NotNull List<Bounty> snapshot) {
        List<Bounty> filtered = filter(snapshot, state);

        int rows = 6;
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / 45.0));
        final var navigating = new boolean[]{false};

        Gui.of(rows)
                .title(Text.toSmallCapsComponent("Bounties"))
                .spamPreventionDuration(110)
                .component(component -> {
                    PagerState<Bounty> pageState = PagerState.of(
                            filtered,
                            GuiLayout.box(Slot.of(1, 1), Slot.of(5, 9))
                    );
                    component.remember(pageState);

                    var sortRef = component.remember(state.sort);

                    component.render(con -> {
                        pageState.forEach(entry -> con.setItem(entry.slot(), buildBountyItem(entry.element())));

                        int page = pageState.getCurrentPage();
                        if (page > 1)          con.setItem(6, 1, prevButton(p, pageState));
                        if (page < totalPages) con.setItem(6, 9, nextButton(p, pageState));

                        con.setItem(6, 4, ItemBuilder.from(Material.HOPPER)
                                .name(Text.toSmallCapsComponent("Sort").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Arrays.stream(BountyListingSortType.values())
                                        .map(s -> (Component) Text.toSmallCapsComponent("▪ " + s)
                                                .color(sortRef.get() == s ? Colors.HOT_PINK : NamedTextColor.WHITE)
                                                .decoration(TextDecoration.ITALIC, false))
                                        .collect(Collectors.toList()))
                                .asGuiItem((player, ctx) -> {
                                    sortRef.update(BountyListingSortType::nextValue);
                                    state.sort = sortRef.get();
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    navigating[0] = true;
                                    schedule(player, () -> buildGui(player, state, BountyManager.getBounties()));
                                }));

                        con.setItem(6, 5, ItemBuilder.from(Material.ANVIL)
                                .name(Text.toSmallCapsComponent("Bounties").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(
                                        Component.text("Click to refresh", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                                        Component.text("Command: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                                                .append(Component.text("/bounty place ", Colors.LIGHT_PINK).decoration(TextDecoration.ITALIC, false))
                                                .append(Component.text("<player> <reward>", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                                )
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_TOAST_OUT, 1f, 1f);
                                    schedule(player, () -> buildGui(player, state, BountyManager.getBounties()));
                                }));

                        con.setItem(6, 6, ItemBuilder.from(Material.OAK_SIGN)
                                .name(Text.toSmallCapsComponent("Search").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to search", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    schedule(player, () -> {
                                        new SignGUI()
                                                .lines(List.of(
                                                        Component.text(""),
                                                        Component.text("↑↑↑↑↑↑↑↑"),
                                                        Component.text("Search")))
                                                .material(Material.OAK_WALL_SIGN)
                                                .action(action -> schedule(action.player(), () -> open(action.player(), action.line(0))))
                                                .open(player);
                                    });
                                }));
                    });
                })
                .build()
                .open(p);
    }

    public static void cleanup(@NotNull UUID playerId) {
        BROWSE_STATES.remove(playerId);
    }
}