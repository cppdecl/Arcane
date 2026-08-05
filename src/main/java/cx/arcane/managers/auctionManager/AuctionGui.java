package cx.arcane.managers.auctionManager;

import cx.arcane.Arcane;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerMeta;
import cx.arcane.utils.*;
import dev.triumphteam.gui.GuiView;
import dev.triumphteam.gui.click.action.EmptyGuiClickAction;
import dev.triumphteam.gui.click.action.SimpleGuiClickAction;
import dev.triumphteam.gui.element.GuiItem;
import dev.triumphteam.gui.layout.GuiLayout;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import dev.triumphteam.gui.slot.Slot;
import dev.triumphteam.gui.state.pagination.PagerState;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class AuctionGui {

    private AuctionGui() {}

    private static final ConcurrentHashMap<UUID, BrowseState> BROWSE_STATES = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault());

    private static final Map<AuctionListingSortType, Comparator<AuctionListing>> COMPARATORS = Map.of(
            AuctionListingSortType.HIGHEST_PRICE,   Comparator.comparingLong(AuctionListing::getPrice).reversed(),
            AuctionListingSortType.LOWEST_PRICE,    Comparator.comparingLong(AuctionListing::getPrice),
            AuctionListingSortType.OLDEST_LISTED,   Comparator.comparing(AuctionListing::getListedAt),
            AuctionListingSortType.RECENTLY_LISTED, Comparator.comparing(AuctionListing::getListedAt).reversed()
    );

    private static final class BrowseState {
        AuctionListingSortType sort = AuctionListingSortType.RECENTLY_LISTED;
        ItemCategory category      = ItemCategory.ALL;
        String query               = "";
    }

    private static List<AuctionListing> filterBrowse(
            @NotNull List<AuctionListing> all, @NotNull BrowseState state, @NotNull UUID viewer) {
        return all.stream()
                .filter(l -> !l.getOwnerId().equals(viewer))
                .filter(l -> state.category == ItemCategory.ALL || l.getCategory() == state.category)
                .filter(l -> ItemUtils.matchesQuery(l.getItem(), state.query))
                .sorted(COMPARATORS.get(state.sort))
                .toList();
    }

    private static List<AuctionListing> filterOwn(@NotNull List<AuctionListing> all, @NotNull UUID owner) {
        return all.stream()
                .filter(l -> l.getOwnerId().equals(owner))
                .sorted(Comparator.comparing(AuctionListing::getListedAt).reversed())
                .toList();
    }

    private static List<AuctionTransaction> filterTx(@NotNull List<AuctionTransaction> all, @NotNull String query) {
        if (query.isBlank()) return all;
        String q = query.toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(t -> {
                    String name = PlainTextComponentSerializer.plainText()
                            .serialize(t.getItem().effectiveName()).toLowerCase(Locale.ROOT);
                    return name.contains(q) || String.valueOf(t.getPrice()).contains(q);
                })
                .toList();
    }

    private static List<Component> buildBaseLore(@NotNull ItemStack stack) {
        List<Component> lore = new ArrayList<>();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta.hasLore() && meta.lore() != null) lore.addAll(meta.lore());
            lore.add(stack.effectiveName().decoration(TextDecoration.ITALIC, false));
            if (!meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS) && meta.hasEnchants()) {
                meta.getEnchants().forEach((e, lvl) ->
                        lore.add(e.displayName(lvl).decoration(TextDecoration.ITALIC, false)));
            }
        }
        return lore;
    }

    private static GuiItem<Player, ItemStack> buildListingItem(@NotNull AuctionListing l, @Nullable String ownerName, boolean own, final @Nullable SimpleGuiClickAction<Player> action) {
        ItemStack stack = l.getItem().clone();

        List<Component> lore = buildBaseLore(stack);
        lore.add(Component.empty());
        lore.add(Component.text("Price: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("$" + Text.formatShortBalance(l.getPrice()), Colors.HOT_PINK)));
        lore.add(Component.empty());
        lore.add(Component.text(own ? "(Click to Cancel)" : "(Click to Purchase)")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        return ItemBuilder.from(stack)
                .name(Component.text(own ? "Your Listing" : (ownerName != null ? ownerName + "'s Listing" : "Listing"))
                        .color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
                .pdc(pdc -> pdc.set(AuctionManager.getListingKey(), PersistentDataType.STRING, l.getId().toString()))
                .asGuiItem(action == null ? new EmptyGuiClickAction<>() : action);
    }

    private static GuiItem<Player, ItemStack> buildListingBuyConfirmItem(@NotNull AuctionListing l, boolean[] navigating) {
        ItemStack stack = l.getItem().clone();
        boolean container = isContainer(stack);

        List<Component> lore = buildBaseLore(stack);
        lore.add(Component.empty());
        lore.add(Component.text("Price: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("$" + Text.formatShortBalance(l.getPrice()), Colors.HOT_PINK)));

        if (container) {
            lore.add(Component.empty());
            lore.add(Component.text("(Click to Inspect Contents)")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }

        ItemBuilder builder = ItemBuilder.from(stack)
                .name(Component.text(l.getOwnerName() + "'s Listing").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
                .pdc(pdc -> pdc.set(AuctionManager.getListingKey(), PersistentDataType.STRING, l.getId().toString()));

        return !container ? builder.asGuiItem() : builder.asGuiItem((player, ctx) -> {
            navigating[0] = true;
            schedule(player, () -> openContainerInspect(player, l.getItem(), ctx.guiView()));
        });
    }

    private static GuiItem<Player, ItemStack> buildTxItem(@NotNull Player viewer, UUID target, @NotNull AuctionTransaction tx, boolean[] navigating) {
        boolean isSeller = tx.getSellerId().equals(target);
        ItemStack stack = tx.getItem().clone();
        boolean container = isContainer(stack);

        String otherName = PlayerManager.getName(isSeller ? tx.getBuyerId() : tx.getSellerId());

        List<Component> lore = buildBaseLore(stack);
        lore.add(Component.empty());
        lore.add(Component.text("For ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("$" + Text.formatShortBalance(tx.getPrice()), Colors.HOT_PINK))
                .append(Component.text(isSeller ? " to " : " from ", Colors.WHITE))
                .append(Component.text(otherName, Colors.HOT_PINK)));
        lore.add(Component.empty());
        lore.add(Component.text(DATE_FMT.format(tx.getTransactedAt()), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("At ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(TIME_FMT.format(tx.getTransactedAt()), Colors.HOT_PINK)));

        if (container) {
            lore.add(Component.empty());
            lore.add(Component.text("(Click to Inspect Contents)")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }

        ItemBuilder builder = ItemBuilder.from(stack)
                .name(Component.text(isSeller ? "Sold" : "Bought")
                        .color(isSeller ? Colors.RED : Colors.HOT_PINK)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        return !container ? builder.asGuiItem() : builder.asGuiItem((player, ctx) -> {
            navigating[0] = true;
            schedule(player, () -> openContainerInspect(player, tx.getItem(), ctx.guiView()));
        });
    }

    private static <T> GuiItem<Player, ItemStack> prevButton(@NotNull Player p, @NotNull PagerState<T> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Previous").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Go to previous page", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                    state.prev();
                });
    }

    private static <T> GuiItem<Player, ItemStack> nextButton(@NotNull Player p, @NotNull PagerState<T> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Next").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Go to next page", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                    state.next();
                });
    }

    private static void schedule(@NotNull Player p, @NotNull Runnable task) {
        FoliaScheduler.getEntityScheduler().execute(p, Arcane.getPlugin(), task, null, 1L);
    }

    // ════════════════════════════════════════════════════════════════════════
    // SHULKER / BUNDLE INSPECTOR
    // ════════════════════════════════════════════════════════════════════════

    private static boolean isContainer(@NotNull ItemStack stack) {
        String name = stack.getType().name();
        return name.contains("SHULKER_BOX") || name.contains("BUNDLE");
    }

    private static List<ItemStack> extractContents(@NotNull ItemStack stack) {
        List<ItemStack> out = new ArrayList<>();
        if (stack.getType().name().contains("SHULKER_BOX")) {
            if (!(stack.getItemMeta() instanceof BlockStateMeta bsm)) return out;
            if (!(bsm.getBlockState() instanceof ShulkerBox box)) return out;
            for (ItemStack i : box.getInventory().getContents()) {
                if (i != null && !i.getType().isAir()) out.add(i);
            }
        } else if (stack.getType().name().contains("BUNDLE")) {
            if (!(stack.getItemMeta() instanceof BundleMeta meta)) return out;
            for (ItemStack i : meta.getItems()) {
                if (i != null && !i.getType().isAir()) out.add(i);
            }
        }
        return out;
    }

    private static void openContainerInspect(@NotNull Player p, @NotNull ItemStack container, @NotNull GuiView parentView) {
        List<ItemStack> contents = extractContents(container);
        final var navigating = new boolean[]{false};

        int maxContentRows = 3;
        int itemsPerPage   = maxContentRows * 7;
        boolean paginated  = contents.size() > itemsPerPage;
        int contentRows    = paginated ? maxContentRows : Math.max(1, (int) Math.ceil(contents.size() / 7.0));
        int rows           = contentRows + 2;
        int totalPages     = paginated ? (contents.size() + itemsPerPage - 1) / itemsPerPage : 1;

        Gui.of(rows)
                .title(container.effectiveName().color(NamedTextColor.DARK_GRAY))
                .onClose(() -> {
                    if (!navigating[0]) {
                        parentView.open();
                        p.playSound(p, Sound.UI_BUTTON_CLICK, 1f, 1f);
                    }
                })
                .component(component -> {
                    if (paginated) {
                        PagerState<ItemStack> pageState = PagerState.of(
                                contents,
                                GuiLayout.box(Slot.of(2, 2), Slot.of(rows - 1, 8))
                        );
                        component.remember(pageState);

                        component.render(con -> {
                            pageState.forEach(entry -> {
                                ItemStack s = entry.element();
                                con.setItem(entry.slot(), buildContainerSlot(p, s, navigating));
                            });

                            int page = pageState.getCurrentPage();
                            if (page > 1)          con.setItem(rows, 1, prevButton(p, pageState));
                            if (page < totalPages) con.setItem(rows, 9, nextButton(p, pageState));

                            con.setItem(rows, 5, backButton(p, navigating, parentView));
                        });
                    } else {
                        component.render(con -> {
                            int index = 0;
                            for (ItemStack s : contents) {
                                int col = (index % 7) + 2;
                                int row = (index / 7) + 2;
                                con.setItem(row, col, buildContainerSlot(p, s, navigating));
                                index++;
                            }
                            con.setItem(rows, 5, backButton(p, navigating, parentView));
                        });
                    }
                })
                .build()
                .open(p);

        p.playSound(p, Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    private static GuiItem<Player, ItemStack> buildContainerSlot(
            @NotNull Player p, @NotNull ItemStack stack, boolean[] navigating) {
        if (!isContainer(stack)) {
            return ItemBuilder.from(stack).asGuiItem((player, ctx) ->
                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f));
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("(Click to Inspect Contents)", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        return ItemBuilder.from(stack)
                .lore(lore)
                .asGuiItem((player, ctx) -> {
                    navigating[0] = true;
                    schedule(player, () -> openContainerInspect(player, stack, ctx.guiView()));
                });
    }

    private static GuiItem<Player, ItemStack> backButton(
            @NotNull Player p, boolean[] navigating, @NotNull GuiView parentView) {
        return ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                .name(Text.toSmallCapsComponent("Back").color(Colors.RED).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to return", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    navigating[0] = true;
                    parentView.open();
                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                });
    }

    // ════════════════════════════════════════════════════════════════════════
    // BROWSE GUI
    // ════════════════════════════════════════════════════════════════════════

    public static void openBrowse(@NotNull Player p, @NotNull String query) {
        BrowseState state = BROWSE_STATES.computeIfAbsent(p.getUniqueId(), k -> new BrowseState());
        state.query = query;
        buildBrowseGui(p, state, AuctionManager.getListings());
    }

    private static void buildBrowseGui(@NotNull Player p, @NotNull BrowseState state, @NotNull List<AuctionListing> snapshot) {
        List<AuctionListing> filtered = filterBrowse(snapshot, state, p.getUniqueId());

        int rows = 6;
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / 45.0));
        final var navigating = new boolean[]{false};

        Gui.of(rows)
                .title(Text.toSmallCapsComponent("Auction"))
                .spamPreventionDuration(110)
                .component(component -> {
                    PagerState<AuctionListing> pageState = PagerState.of(
                            filtered,
                            GuiLayout.box(Slot.of(1, 1), Slot.of(5, 9))
                    );
                    component.remember(pageState);

                    var sortRef = component.remember(state.sort);
                    var catRef  = component.remember(state.category);

                    component.render(con -> {
                        pageState.forEach(entry -> {
                            AuctionListing listing = entry.element();
                            String ownerName = PlayerManager.getName(listing.getOwnerId());
                            con.setItem(entry.slot(), buildListingItem(listing, ownerName, false, (player, ctx) -> {
                                if (listing.getOwnerId().equals(player.getUniqueId())) {
                                    AuctionManager.sendError(player, "You can't buy your own listing!");
                                    return;
                                }
                                AuctionListing fresh = AuctionManager.getListing(listing.getId());
                                if (fresh == null) {
                                    AuctionManager.sendError(player, "That listing no longer exists!");
                                    return;
                                }
                                navigating[0] = true;
                                ctx.guiView().close();

                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> openBuyConfirm(player, fresh, state));
                            }));
                        });

                        int page = pageState.getCurrentPage();
                        if (page > 1)          con.setItem(6, 1, prevButton(p, pageState));
                        if (page < totalPages) con.setItem(6, 9, nextButton(p, pageState));

                        con.setItem(6, 3, ItemBuilder.from(Material.CAULDRON)
                                .name(Text.toSmallCapsComponent("Sort").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Arrays.stream(AuctionListingSortType.values())
                                        .map(s -> (Component) Text.toSmallCapsComponent("▪ " + s)
                                                .color(sortRef.get() == s ? Colors.HOT_PINK : NamedTextColor.WHITE)
                                                .decoration(TextDecoration.ITALIC, false))
                                        .collect(Collectors.toList()))
                                .asGuiItem((player, ctx) -> {
                                    sortRef.update(AuctionListingSortType::next);
                                    state.sort = sortRef.get();
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    navigating[0] = true;
                                    schedule(player, () -> buildBrowseGui(player, state, AuctionManager.getListings()));
                                }));

                        con.setItem(6, 4, ItemBuilder.from(Material.HOPPER)
                                .name(Text.toSmallCapsComponent("Filter").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Arrays.stream(ItemCategory.values())
                                        .map(c -> (Component) Text.toSmallCapsComponent("▪ " + c)
                                                .color(catRef.get() == c ? Colors.HOT_PINK : NamedTextColor.WHITE)
                                                .decoration(TextDecoration.ITALIC, false))
                                        .collect(Collectors.toList()))
                                .asGuiItem((player, ctx) -> {
                                    catRef.update(cur -> {
                                        ItemCategory[] cats = ItemCategory.values();
                                        return cats[(cur.ordinal() + 1) % cats.length];
                                    });
                                    state.category = catRef.get();
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    navigating[0] = true;
                                    schedule(player, () -> buildBrowseGui(player, state, AuctionManager.getListings()));
                                }));

                        con.setItem(6, 5, ItemBuilder.from(Material.ANVIL)
                                .name(Text.toSmallCapsComponent("Auction").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to refresh", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_TOAST_OUT, 1f, 1f);
                                    schedule(player, () -> buildBrowseGui(player, state, AuctionManager.getListings()));
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
                                                        Component.text("↑↑↑↑↑↑↑↑").color(Colors.HOT_PINK),
                                                        Component.text("Search")))
                                                .material(Material.OAK_WALL_SIGN)
                                                .action(action -> schedule(action.player(), () -> openBrowse(action.player(), action.line(0))))
                                                .open(player);
                                    });
                                }));

                        con.setItem(6, 7, ItemBuilder.from(Material.CHEST)
                                .name(Text.toSmallCapsComponent("Your Listings").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to manage your listings", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    schedule(player, () -> openListings(player));
                                }));
                    });
                })
                .build()
                .open(p);
    }

    // ════════════════════════════════════════════════════════════════════════
    // BUY CONFIRM GUI
    // ════════════════════════════════════════════════════════════════════════

    private static void openBuyConfirm(@NotNull Player p, @NotNull AuctionListing listing, @NotNull BrowseState state) {
        List<ItemStack> contents = extractContents(listing.getItem());
        final boolean hasContents = !contents.isEmpty();
        final AtomicBoolean confirmed = new AtomicBoolean(false);
        final var navigating = new boolean[]{false};

        String ownerName = PlayerManager.getName(listing.getOwnerId());
        int rows = 3;

        Gui.of(rows)
                .title(Text.toSmallCapsComponent(hasContents ? "Confirm Purchase & Preview" : "Confirm Purchase"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0]) {
                        schedule(p, () -> buildBrowseGui(p, state, AuctionManager.getListings()));
                    }
                })
                .statelessComponent(con -> {
                    // Center item — clickable to inspect if it's a container
                    con.setItem(2, 5, buildListingBuyConfirmItem(listing, navigating));

                    con.setItem(2, 3, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Cancel").color(Colors.RED).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to cancel", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> buildBrowseGui(player, state, AuctionManager.getListings()));
                            }));

                    con.setItem(2, 7, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Confirm").color(TextColor.color(0x00ff00)).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to confirm purchase", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                if (!confirmed.compareAndSet(false, true)) return;
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);

                                AuctionManager.BuyResult result = AuctionManager.executeBuy(player, listing.getId());
                                switch (result) {
                                    case SUCCESS     -> schedule(player, () -> buildBrowseGui(player, state, AuctionManager.getListings()));
                                    case NOT_FOUND   -> { confirmed.set(false); AuctionManager.sendError(player, "That listing no longer exists!"); }
                                    case OWN_LISTING -> { confirmed.set(false); AuctionManager.sendError(player, "You can't buy your own listing!"); }
                                    case NO_MONEY    -> { confirmed.set(false); AuctionManager.sendError(player, "You don't have enough money!"); }
                                    case NO_SPACE    -> { confirmed.set(false); AuctionManager.sendError(player, "Your inventory is full!"); }
                                }
                            }));
                })
                .build()
                .open(p);
    }

    // ════════════════════════════════════════════════════════════════════════
    // OWN LISTINGS GUI
    // ════════════════════════════════════════════════════════════════════════

    public static void openListings(@NotNull Player p) {
        buildListingsGui(p, AuctionManager.getListings());
    }

    private static void buildListingsGui(@NotNull Player p, @NotNull List<AuctionListing> snapshot) {
        List<AuctionListing> own = filterOwn(snapshot, p.getUniqueId());

        int rows = 6;
        int totalPages = Math.max(1, (int) Math.ceil(own.size() / 45.0));
        final var navigating = new boolean[]{false};

        Gui.of(rows)
                .title(Text.toSmallCapsComponent("Auction \u2192 Listings"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0]) {
                        schedule(p, () -> openBrowse(p, ""));
                    }
                })
                .component(component -> {
                    PagerState<AuctionListing> pageState = PagerState.of(
                            own,
                            GuiLayout.box(Slot.of(1, 1), Slot.of(5, 9))
                    );
                    component.remember(pageState);

                    component.render(con -> {
                        pageState.forEach(entry -> {
                            AuctionListing listing = entry.element();
                            con.setItem(entry.slot(), buildListingItem(listing, null, true, (player, ctx) -> {
                                UUID id = listing.getId();
                                if (!AuctionManager.ownsListing(player.getUniqueId(), id)) {
                                    AuctionManager.sendError(player, "That listing no longer exists!");
                                    return;
                                }
                                AuctionListing l = AuctionManager.getListing(id);
                                if (l == null || !AuctionManager.removeListing(id)) {
                                    AuctionManager.sendError(player, "That listing no longer exists!");
                                    return;
                                }
                                AuctionManager.returnItem(player, l.getItem());
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                player.playSound(player, Sound.BLOCK_CRAFTER_FAIL, 1f, 1f);
                                navigating[0] = true;
                                schedule(player, () -> buildListingsGui(player, AuctionManager.getListings()));
                            }));
                        });

                        int page = pageState.getCurrentPage();
                        if (page > 1)          con.setItem(6, 1, prevButton(p, pageState));
                        if (page < totalPages) con.setItem(6, 9, nextButton(p, pageState));

                        con.setItem(6, 4, ItemBuilder.from(Material.WRITABLE_BOOK)
                                .name(Text.toSmallCapsComponent("Transactions").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("View your purchases & sales", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    schedule(player, () -> openTransactions(player, player.getUniqueId(), ""));
                                }));

                        con.setItem(6, 5, ItemBuilder.from(Material.ANVIL)
                                .name(Text.toSmallCapsComponent("Auction").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to refresh", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_TOAST_OUT, 1f, 1f);
                                    schedule(player, () -> buildListingsGui(player, AuctionManager.getListings()));
                                }));

                        con.setItem(6, 6, ItemBuilder.from(Material.PAPER)
                                .name(Text.toSmallCapsComponent("Sell Item").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Use /ah sell <price> with item in hand", NamedTextColor.WHITE)
                                        .decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f)));
                    });
                })
                .build()
                .open(p);
    }

    // ════════════════════════════════════════════════════════════════════════
    // TRANSACTIONS GUI
    // ════════════════════════════════════════════════════════════════════════

    public static void openTransactions(Player p, @NotNull UUID target, @NotNull String query) {
        buildTransactionsGui(p, target, AuctionManager.getPlayerTransactions(target), query);
    }

    private static void buildTransactionsGui(@NotNull Player p, UUID target, @NotNull List<AuctionTransaction> snapshot, @NotNull String query) {
        List<AuctionTransaction> filtered = filterTx(snapshot, query);

        PlayerMeta pMeta = PlayerManager.getByUniqueId(p.getUniqueId()).getMeta();

        int rows = 6;
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / 45.0));
        final var navigating = new boolean[]{false};

        Gui.of(rows)
                .title(Text.toSmallCapsComponent("Auction \u2192 History"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0]) {
                        schedule(p, () -> openListings(p));
                    }
                })
                .component(component -> {
                    PagerState<AuctionTransaction> pageState = PagerState.of(
                            filtered,
                            GuiLayout.box(Slot.of(1, 1), Slot.of(5, 9))
                    );
                    component.remember(pageState);

                    component.render(con -> {
                        pageState.forEach(entry -> con.setItem(entry.slot(), buildTxItem(p, target, entry.element(), navigating)));

                        int page = pageState.getCurrentPage();
                        if (page > 1)          con.setItem(6, 1, prevButton(p, pageState));
                        if (page < totalPages) con.setItem(6, 9, nextButton(p, pageState));

                        con.setItem(6, 4, ItemBuilder.from(Material.WRITABLE_BOOK)
                                .name(Text.toSmallCapsComponent("Stats").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(
                                        Component.text("Total Spent: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                                                .append(Component.text("$" + Text.formatShortBalance(pMeta.getTotalAuctionSpent()), Colors.HOT_PINK)),
                                        Component.text("Total Earned: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                                                .append(Component.text("$" + Text.formatShortBalance(pMeta.getTotalAuctionEarned()), Colors.HOT_PINK))
                                )
                                .asGuiItem((player, ctx) -> player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f)));

                        con.setItem(6, 5, ItemBuilder.from(Material.ANVIL)
                                .name(Text.toSmallCapsComponent("Transactions").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to refresh", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_TOAST_OUT, 1f, 1f);
                                    schedule(player, () -> buildTransactionsGui(player, player.getUniqueId(), AuctionManager.getPlayerTransactions(player.getUniqueId()), query));
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
                                                        Component.text("↑↑↑↑↑↑↑↑").color(Colors.HOT_PINK),
                                                        Component.text("Search")))
                                                .material(Material.OAK_WALL_SIGN)
                                                .action(action -> schedule(action.player(), () -> openTransactions(action.player(), target, action.line(0))))
                                                .open(player);
                                    });
                                }));
                    });
                })
                .build()
                .open(p);
    }

    // ════════════════════════════════════════════════════════════════════════
    // SELL CONFIRM GUI
    // ════════════════════════════════════════════════════════════════════════

    public static void openSellConfirm(@NotNull Player p, @NotNull ItemStack item, long price, boolean removeFromHand) {
        AtomicBoolean confirmed = new AtomicBoolean(false);
        final var navigating = new boolean[]{false};

        List<Component> lore = buildBaseLore(item);
        lore.add(Component.empty());
        lore.add(Component.text("Price: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("$" + Text.formatShortBalance(price), Colors.HOT_PINK)));

        GuiItem<Player, ItemStack> previewItem = ItemBuilder.from(item.clone())
                .name(Text.toSmallCapsComponent("Item To List").color(Colors.HOT_PINK))
                .lore(lore)
                .flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
                .asGuiItem();

        Gui.of(3)
                .title(Text.toSmallCapsComponent("Confirm Listing"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0] && !removeFromHand) {
                        AuctionManager.returnItem(p, item);
                    }
                })
                .statelessComponent(con -> {
                    con.setItem(2, 5, previewItem);

                    con.setItem(2, 3, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Cancel").color(Colors.RED).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to cancel", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                ctx.guiView().close();
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                if (!removeFromHand) AuctionManager.returnItem(player, item);
                            }));

                    con.setItem(2, 7, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Confirm").color(TextColor.color(0x00ff00)).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to list this item", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                if (!confirmed.compareAndSet(false, true)) return;

                                if (removeFromHand && !player.getInventory().containsAtLeast(item, item.getAmount())) {
                                    AuctionManager.sendError(player, "You no longer have that item!");
                                    confirmed.set(false);
                                    return;
                                }

                                if (!AuctionManager.canSell(player, item, price)) {
                                    if (!removeFromHand) AuctionManager.returnItem(player, item);
                                    confirmed.set(false);
                                    return;
                                }

                                navigating[0] = true;
                                ctx.guiView().close();
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                AuctionManager.executeSell(player, item, price, removeFromHand);
                            }));
                })
                .build()
                .open(p);
    }

    public static void cleanup(@NotNull UUID playerId) {
        BROWSE_STATES.remove(playerId);
    }
}