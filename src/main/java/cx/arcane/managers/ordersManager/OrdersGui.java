package cx.arcane.managers.ordersManager;

import cx.arcane.Arcane;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.priceManager.PriceManager;
import cx.arcane.utils.*;
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
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class OrdersGui {

    private OrdersGui() {}

    // ── Item Picker internals ────────────────────────────────────────────────

    private static final List<Material> PICKER_MATERIALS = Registry.MATERIAL.stream()
            .filter(mat -> mat.isItem() && !mat.isAir() && !mat.isLegacy())
            .toList();

    private static final Map<OrderPickerSortType, Comparator<Material>> PICKER_SORT_COMPARATORS = Map.of(
            OrderPickerSortType.A_Z,           Comparator.comparing(Material::name),
            OrderPickerSortType.Z_A,           Comparator.comparing(Material::name).reversed(),
            OrderPickerSortType.HIGHEST_PRICE, Comparator.comparingDouble((Material m) -> PriceManager.getSellPrice(m)).reversed().thenComparing(Material::name),
            OrderPickerSortType.LOWEST_PRICE,  Comparator.comparingDouble((Material m) -> PriceManager.getSellPrice(m)).thenComparing(Material::name)
    );

    private static List<Material> getPickerFiltered(
            OrderPickerSortType sortType, ItemCategory category, String searchQuery) {
        return PICKER_MATERIALS.stream()
                .filter(mat -> category == ItemCategory.ALL || ItemUtils.getCategory(new ItemStack(mat)) == category)
                .filter(mat -> searchQuery == null || searchQuery.isBlank() || ItemUtils.matchesQuery(new ItemStack(mat), searchQuery))
                .sorted(PICKER_SORT_COMPARATORS.getOrDefault(sortType, Comparator.comparing(Material::name)))
                .toList();
    }

    // ── Browse state ─────────────────────────────────────────────────────────

    private static final ConcurrentHashMap<UUID, BrowseState> BROWSE_STATES = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault());

    private static final Map<OrderListingSortType, Comparator<OrderListing>> COMPARATORS = Map.of(
            OrderListingSortType.MOST_PAID,            Comparator.comparingLong(OrderListing::getPrice).reversed(),
            OrderListingSortType.LEAST_PAID,           Comparator.comparingLong(OrderListing::getPrice),
            OrderListingSortType.MOST_DELIVERED,       Comparator.comparingLong(OrderListing::getDeliveredAmount).reversed(),
            OrderListingSortType.LEAST_DELIVERED,      Comparator.comparingLong(OrderListing::getDeliveredAmount),
            OrderListingSortType.MOST_MONEY_PER_ITEM,  Comparator.comparingLong(OrderListing::getPrice).reversed(),
            OrderListingSortType.LEAST_MONEY_PER_ITEM, Comparator.comparingLong(OrderListing::getPrice),
            OrderListingSortType.OLDEST_LISTED,        Comparator.comparing(OrderListing::getListedAt),
            OrderListingSortType.RECENTLY_LISTED,      Comparator.comparing(OrderListing::getListedAt).reversed()
    );

    private static final class BrowseState {
        OrderListingSortType sort = OrderListingSortType.MOST_PAID;
        ItemCategory category     = ItemCategory.ALL;
        String query              = "";
    }

    // ── Internal utils ───────────────────────────────────────────────────────

    private static void schedule(@NotNull Player p, @NotNull Runnable task) {
        FoliaScheduler.getEntityScheduler().execute(p, Arcane.getPlugin(), task, null, 1L);
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

    // ── Filters ──────────────────────────────────────────────────────────────

    private static List<OrderListing> filterBrowse(
            @NotNull List<OrderListing> all, @NotNull BrowseState state, @NotNull UUID viewer) {
        return all.stream()
                .filter(l -> !l.getOwnerId().equals(viewer))
                .filter(OrderListing::canDeliver)
                .filter(l -> state.category == ItemCategory.ALL || l.getCategory() == state.category)
                .filter(l -> ItemUtils.matchesQuery(l.getItem(), state.query))
                .sorted(COMPARATORS.get(state.sort))
                .toList();
    }

    private static List<OrderListing> filterOwn(@NotNull List<OrderListing> all, @NotNull UUID owner) {
        return all.stream()
                .filter(l -> l.getOwnerId().equals(owner))
                .sorted(Comparator.comparing(OrderListing::getListedAt).reversed())
                .toList();
    }

    private static List<OrderTransaction> filterTx(@NotNull List<OrderTransaction> all, @NotNull String query) {
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

    // ── Item builders ────────────────────────────────────────────────────────

    private static GuiItem<Player, ItemStack> buildOrderItem(
            @NotNull OrderListing l, boolean own, @Nullable SimpleGuiClickAction<Player> action) {

        String ownerName = PlayerManager.getName(l.getOwnerId());
        ItemStack stack  = l.getItem().clone();

        List<Component> lore = buildBaseLore(stack);
        lore.add(Component.empty());
        lore.add(Component.text("Pays ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("$" + Text.formatShortBalance(l.getPrice()), Colors.HOT_PINK))
                .append(Component.text(" Each", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("Progress: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(
                        Text.formatShortBalance(l.getDeliveredAmount()) + "/" + Text.formatShortBalance(l.getAmount()),
                        Colors.HOT_PINK)));
        lore.add(Component.text("Total Paid: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(
                        "$" + Text.formatShortBalance(l.getDeliveredAmount() * l.getPrice()) +
                                "/$" + Text.formatShortBalance(l.getAmount() * l.getPrice()),
                        Colors.HOT_PINK)));
        lore.add(Component.empty());
        lore.add(l.getStatusComponent());
        lore.add(Component.empty());
        lore.add(Component.text(own ? "(Click to Manage)" : "(Click to Deliver)")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        return ItemBuilder.from(stack)
                .name(Component.text(own ? "Your Order" : (ownerName != null ? ownerName + "'s Order" : "Order"))
                        .color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
                .pdc(pdc -> pdc.set(OrdersManager.getOrderListingKey(), PersistentDataType.STRING, l.getId().toString()))
                .asGuiItem(action == null ? new EmptyGuiClickAction<>() : action);
    }

    private static GuiItem<Player, ItemStack> buildOrderManageItem(@NotNull OrderListing l) {
        ItemStack stack = l.getItem().clone();

        List<Component> lore = buildBaseLore(stack);
        lore.add(Component.empty());
        lore.add(Component.text("Pays ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("$" + Text.formatShortBalance(l.getPrice()), Colors.HOT_PINK))
                .append(Component.text(" Each", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("Progress: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(
                        Text.formatShortBalance(l.getDeliveredAmount()) + "/" + Text.formatShortBalance(l.getAmount()),
                        Colors.HOT_PINK)));
        if (l.canCollect()) {
            lore.add(Component.text("Collectible: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(
                            Text.formatShortBalance(l.getDeliveredAmount() - l.getCollectedAmount()),
                            Colors.HOT_PINK)));
        }
        lore.add(Component.empty());
        lore.add(l.getStatusComponent());

        return ItemBuilder.from(stack)
                .name(Component.text("Your Order").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
                .asGuiItem();
    }

    private static GuiItem<Player, ItemStack> buildTxItem(
            @NotNull Player viewer, @NotNull UUID target, @NotNull OrderTransaction tx) {
        boolean isSeller = tx.getSellerId().equals(target);
        ItemStack stack  = tx.getItem().clone();
        String otherName = PlayerManager.getName(isSeller ? tx.getBuyerId() : tx.getSellerId());

        List<Component> lore = buildBaseLore(stack);
        lore.add(Component.empty());
        lore.add(Component.text("Amount: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(Text.formatShortBalance(tx.getAmount()), Colors.HOT_PINK)));
        lore.add(Component.text("For: ", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("$" + Text.formatShortBalance(tx.getPrice()), Colors.HOT_PINK))
                .append(Component.text(isSeller ? " from " : " to ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .append(Component.text(otherName != null ? otherName : "Unknown", Colors.HOT_PINK)));
        lore.add(Component.empty());
        lore.add(Component.text(DATE_FMT.format(tx.getTransactedAt()), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("At ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(TIME_FMT.format(tx.getTransactedAt()), Colors.HOT_PINK)));

        return ItemBuilder.from(stack)
                .name(Component.text(isSeller ? "Delivered" : "Ordered")
                        .color(isSeller ? Colors.HOT_PINK : Colors.RED)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
                .asGuiItem((player, ctx) -> player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f));
    }

    // ════════════════════════════════════════════════════════════════════════
    // BROWSE GUI
    // ════════════════════════════════════════════════════════════════════════

    public static void openBrowse(@NotNull Player p, @NotNull String query) {
        BrowseState state = BROWSE_STATES.computeIfAbsent(p.getUniqueId(), k -> new BrowseState());
        state.query = query;
        buildBrowseGui(p, state, OrdersManager.getListings());
    }

    private static void buildBrowseGui(
            @NotNull Player p, @NotNull BrowseState state, @NotNull List<OrderListing> snapshot) {
        List<OrderListing> filtered = filterBrowse(snapshot, state, p.getUniqueId());

        int rows       = 6;
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / 45.0));
        final var navigating = new boolean[]{false};

        Gui.of(rows)
                .title(Text.toSmallCapsComponent("Orders"))
                .spamPreventionDuration(110)
                .component(component -> {
                    PagerState<OrderListing> pageState = PagerState.of(
                            filtered, GuiLayout.box(Slot.of(1, 1), Slot.of(5, 9)));
                    component.remember(pageState);

                    var sortRef = component.remember(state.sort);
                    var catRef  = component.remember(state.category);

                    component.render(con -> {
                        pageState.forEach(entry -> {
                            OrderListing listing = entry.element();
                            con.setItem(entry.slot(), buildOrderItem(listing, false, (player, ctx) -> {
                                OrderListing fresh = OrdersManager.getListing(listing.getId());
                                if (fresh == null || !fresh.canDeliver()) {
                                    OrdersManager.sendError(player, "That order is no longer available!");
                                    return;
                                }
                                navigating[0] = true;
                                ctx.guiView().close();
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> schedule(player, () -> openDeliverGui(player, fresh, state)));
                            }));
                        });

                        int page = pageState.getCurrentPage();
                        if (page > 1)          con.setItem(6, 1, prevButton(p, pageState));
                        if (page < totalPages) con.setItem(6, 9, nextButton(p, pageState));

                        con.setItem(6, 3, ItemBuilder.from(Material.CAULDRON)
                                .name(Text.toSmallCapsComponent("Sort").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Arrays.stream(OrderListingSortType.values())
                                        .map(s -> (Component) Text.toSmallCapsComponent("▪ " + s)
                                                .color(sortRef.get() == s ? Colors.HOT_PINK : NamedTextColor.WHITE)
                                                .decoration(TextDecoration.ITALIC, false))
                                        .collect(Collectors.toList()))
                                .asGuiItem((player, ctx) -> {
                                    sortRef.update(OrderListingSortType::next);
                                    state.sort = sortRef.get();
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    navigating[0] = true;
                                    schedule(player, () -> buildBrowseGui(player, state, OrdersManager.getListings()));
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
                                    schedule(player, () -> buildBrowseGui(player, state, OrdersManager.getListings()));
                                }));

                        con.setItem(6, 5, ItemBuilder.from(Material.MAP)
                                .name(Text.toSmallCapsComponent("Orders").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to refresh", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_TOAST_OUT, 1f, 1f);
                                    schedule(player, () -> buildBrowseGui(player, state, OrdersManager.getListings()));
                                }));

                        con.setItem(6, 6, ItemBuilder.from(Material.OAK_SIGN)
                                .name(Text.toSmallCapsComponent("Search").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to search", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    schedule(player, () -> new SignGUI()
                                            .lines(List.of(
                                                    Component.text(""),
                                                    Component.text("↑↑↑↑↑↑↑↑").color(Colors.HOT_PINK),
                                                    Component.text("Search")))
                                            .material(Material.OAK_WALL_SIGN)
                                            .action(action -> schedule(action.player(),
                                                    () -> openBrowse(action.player(), action.line(0))))
                                            .open(player));
                                }));

                        con.setItem(6, 7, ItemBuilder.from(Material.CHEST)
                                .name(Text.toSmallCapsComponent("Your Orders").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to manage your orders", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    schedule(player, () -> openMyOrders(player));
                                }));
                    });
                })
                .build()
                .open(p);
    }

    // ════════════════════════════════════════════════════════════════════════
    // DELIVER GUI
    // ════════════════════════════════════════════════════════════════════════

    private static void openDeliverGui(
            @NotNull Player p, @NotNull OrderListing listing, @NotNull BrowseState state) {

        final boolean[] reopening = {false};

        BareInventoryGUI gui = new BareInventoryGUI()
                .title(Text.toSmallCaps("Deliver Items"))
                .rows(6);

        gui.onClick(e -> {
            if (!(e.getWhoClicked() instanceof Player player)) return;

            Inventory top = e.getInventory();

            schedule(player, () -> {
                if (OrdersManager.isInventoryFull(top)) {
                    reopening[0] = true;
                    player.closeInventory();
                }
            });
        });

        gui.onClose(e -> {
            Inventory inv = gui.getInventory();

            if (reopening[0]) {
                reopening[0] = false;
                runDelivery(p, inv, listing, state, true);
            } else {
                runDelivery(p, inv, listing, state, false);
            }
        });

        gui.open(p);
    }

    private static void runDelivery(
            @NotNull Player p, @NotNull Inventory inv,
            @NotNull OrderListing listing, @NotNull BrowseState state,
            boolean reopen) {

        long deliverable = OrdersManager.countDeliverableItems(inv, listing);

        if (deliverable <= 0) {
            OrdersManager.returnInventory(p, inv);
            schedule(p, () -> buildBrowseGui(p, state, OrdersManager.getListings()));
            return;
        }

        OrdersManager.DeliveryOutcome outcome = OrdersManager.processDelivery(
                inv, listing.getId(), p.getUniqueId(), o -> {});

        OrdersManager.returnItems(p, outcome.rejected);

        if (outcome.result == OrdersManager.DeliverResult.SUCCESS) {
            long earned = outcome.delivered * listing.getPrice();
            TextComponent res = Component.text("You delivered ", NamedTextColor.GRAY)
                    .append(Component.text(Text.formatShortBalance(outcome.delivered) + " ", Colors.HOT_PINK))
                    .append(listing.getItem().effectiveName().color(Colors.HOT_PINK))
                    .append(Component.text(" and earned ", NamedTextColor.GRAY))
                    .append(Component.text("$" + Text.formatShortBalance(earned), Colors.HOT_PINK));
            p.sendMessage(res);
            p.sendActionBar(res);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f);
        } else {
            OrdersManager.sendError(p, switch (outcome.result) {
                case NOTHING_TO_DELIVER -> "Nothing to deliver!";
                case FULFILLED          -> "That order is already fulfilled!";
                default                 -> "That order no longer exists!";
            });
        }

        OrderListing updated = OrdersManager.getListing(listing.getId());
        if (reopen && updated != null && updated.canDeliver()) {
            schedule(p, () -> openDeliverGui(p, updated, state));
        } else {
            schedule(p, () -> buildBrowseGui(p, state, OrdersManager.getListings()));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // MY ORDERS GUI
    // ════════════════════════════════════════════════════════════════════════

    public static void openMyOrders(@NotNull Player p) {
        buildMyOrdersGui(p, OrdersManager.getListings());
    }

    private static void buildMyOrdersGui(@NotNull Player p, @NotNull List<OrderListing> snapshot) {
        List<OrderListing> own = filterOwn(snapshot, p.getUniqueId());

        int rows       = 6;
        int totalPages = Math.max(1, (int) Math.ceil(own.size() / 45.0));
        final var navigating = new boolean[]{false};

        Gui.of(rows)
                .title(Text.toSmallCapsComponent("Orders \u2192 My Orders"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0]) schedule(p, () -> openBrowse(p, ""));
                })
                .component(component -> {
                    PagerState<OrderListing> pageState = PagerState.of(
                            own, GuiLayout.box(Slot.of(1, 1), Slot.of(5, 9)));
                    component.remember(pageState);

                    component.render(con -> {
                        pageState.forEach(entry -> {
                            OrderListing listing = entry.element();
                            con.setItem(entry.slot(), buildOrderItem(listing, true, (player, ctx) -> {
                                OrderListing fresh = OrdersManager.getListing(listing.getId());
                                if (fresh == null || !OrdersManager.ownsListing(player.getUniqueId(), listing.getId())) {
                                    OrdersManager.sendError(player, "That order no longer exists!");
                                    return;
                                }
                                navigating[0] = true;
                                ctx.guiView().close();
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> openManageOrder(player, fresh));
                            }));
                        });

                        int page = pageState.getCurrentPage();
                        if (page > 1)          con.setItem(6, 1, prevButton(p, pageState));
                        if (page < totalPages) con.setItem(6, 9, nextButton(p, pageState));

                        con.setItem(6, 4, ItemBuilder.from(Material.WRITABLE_BOOK)
                                .name(Text.toSmallCapsComponent("Transactions").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("View your delivery history", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    schedule(player, () -> openTransactions(player, player.getUniqueId(), ""));
                                }));

                        con.setItem(6, 5, ItemBuilder.from(Material.MAP)
                                .name(Text.toSmallCapsComponent("Orders").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to refresh", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_TOAST_OUT, 1f, 1f);
                                    schedule(player, () -> buildMyOrdersGui(player, OrdersManager.getListings()));
                                }));

                        con.setItem(6, 6, ItemBuilder.from(Material.PAPER)
                                .name(Text.toSmallCapsComponent("Place Order").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to place a new order", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    schedule(player, () -> openCreateOrder(player));
                                }));
                    });
                })
                .build()
                .open(p);
    }

    // ════════════════════════════════════════════════════════════════════════
    // MANAGE ORDER GUI
    // ════════════════════════════════════════════════════════════════════════

    private static void openManageOrder(@NotNull Player p, @NotNull OrderListing listing) {
        final var navigating = new boolean[]{false};

        Gui.of(3)
                .title(Text.toSmallCapsComponent("Orders \u2192 Manage"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0]) schedule(p, () -> buildMyOrdersGui(p, OrdersManager.getListings()));
                })
                .statelessComponent(con -> {
                    con.setItem(2, 5, buildOrderManageItem(listing));

                    if (listing.canCollect()) {
                        con.setItem(2, 3, ItemBuilder.from(Material.CHEST)
                                .name(Text.toSmallCapsComponent("Collect").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(
                                        Component.text("Click to collect delivered items", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                                        Component.text("Available: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                                                .append(Component.text(
                                                        Text.formatShortBalance(listing.getDeliveredAmount() - listing.getCollectedAmount()),
                                                        Colors.HOT_PINK))
                                )
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    ctx.guiView().close();
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    schedule(player, () -> schedule(player, () -> openCollect(player, listing)));
                                }));
                    } else {
                        con.setItem(2, 3, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                                .name(Text.toSmallCapsComponent("Collect").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("No items to collect yet", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem());
                    }

                    if (listing.canDelete()) {
                        con.setItem(2, 7, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                                .name(Text.toSmallCapsComponent("Cancel Order").color(Colors.RED).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to cancel this order", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    if (!OrdersManager.removeListing(listing.getId())) {
                                        OrdersManager.sendError(player, "That order no longer exists!");
                                        return;
                                    }
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    player.playSound(player, Sound.BLOCK_CRAFTER_FAIL, 1f, 1f);

                                    long refund = (listing.getAmount() - listing.getDeliveredAmount()) * listing.getPrice();
                                    if (refund > 0) EcoManager.giveMoney(player.getUniqueId(), refund);

                                    schedule(player, () -> buildMyOrdersGui(player, OrdersManager.getListings()));
                                }));
                    } else {
                        con.setItem(2, 7, ItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE)
                                .name(Text.toSmallCapsComponent("Cancel Order").color(Colors.RED).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Collect your items first!", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem());
                    }
                })
                .build()
                .open(p);
    }

    // ════════════════════════════════════════════════════════════════════════
    // COLLECT GUI
    // ════════════════════════════════════════════════════════════════════════

    private static void openCollect(@NotNull Player p, @NotNull OrderListing listing) {
        long available = listing.getDeliveredAmount() - listing.getCollectedAmount();
        if (available <= 0) {
            OrdersManager.sendError(p, "No items to collect!");
            schedule(p, () -> openManageOrder(p, listing));
            return;
        }

        final long[]    filled    = {0};
        final boolean[] reopening = {false};

        BareInventoryGUI gui = new BareInventoryGUI()
                .title(Text.toSmallCaps("Collect Items"))
                .rows(6);

        fillCollectGui(gui.getInventory(), listing, filled);

        gui.onClick(e -> {
            if (!(e.getWhoClicked() instanceof Player player)) return;

            Inventory top         = e.getInventory();
            boolean   clickedTop  = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
            boolean   clickedBot  = e.getClickedInventory() != null && !e.getClickedInventory().equals(top);

            switch (e.getAction()) {
                case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> {
                    if (clickedTop) { e.setCancelled(true); return; }
                }
                case MOVE_TO_OTHER_INVENTORY -> {
                    if (clickedBot) { e.setCancelled(true); return; }
                }
                case COLLECT_TO_CURSOR -> { e.setCancelled(true); return; }
                default -> { if (!clickedTop) return; }
            }

            schedule(player, () -> {
                long current = OrdersManager.countItems(top);
                long delta   = filled[0] - current;
                if (delta <= 0) return;

                listing.increaseCollectedAmount(delta);
                filled[0] = current;

                if (current == 0) {
                    reopening[0] = true;
                    player.closeInventory();
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f);
                }
            });
        });

        gui.onClose(e -> {
            if (reopening[0]) {
                reopening[0] = false;
                long remaining = listing.getDeliveredAmount() - listing.getCollectedAmount();
                if (remaining > 0) {
                    schedule(p, () -> openCollect(p, listing));
                } else {
                    afterCollect(p, listing);
                }
            } else {
                afterCollect(p, listing);
            }
        });

        gui.open(p);
    }

    private static void afterCollect(@NotNull Player p, @NotNull OrderListing listing) {
        if (listing.isFulfilled() && listing.isCollected()) {
            OrdersManager.removeListing(listing.getId());
            schedule(p, () -> buildMyOrdersGui(p, OrdersManager.getListings()));
        } else {
            schedule(p, () -> openManageOrder(p, listing));
        }
    }

    private static void fillCollectGui(@NotNull Inventory inv, @NotNull OrderListing listing, long[] filled) {
        inv.clear();
        long available = listing.getDeliveredAmount() - listing.getCollectedAmount();
        ItemStack base = listing.getItem().clone();
        int maxStack   = base.getMaxStackSize();
        long remaining = available;
        int slot       = 0;

        while (remaining > 0 && slot < 54) {
            int give        = (int) Math.min(maxStack, remaining);
            ItemStack stack = base.clone();
            stack.setAmount(give);
            inv.setItem(slot, stack);
            remaining -= give;
            slot++;
        }

        filled[0] = available - remaining;
    }

    // ════════════════════════════════════════════════════════════════════════
    // CREATE ORDER GUI
    // ════════════════════════════════════════════════════════════════════════

    public static void openCreateOrder(@NotNull Player p) {
        openCreateOrder(p, new ItemStack(Material.STONE), 1L, 1L);
    }

    private static void openCreateOrder(@NotNull Player p, @NotNull ItemStack item, long amount, long price) {
        final var navigating = new boolean[]{false};

        Gui.of(3)
                .title(Text.toSmallCapsComponent("Orders \u2192 New Order"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0]) schedule(p, () -> openMyOrders(p));
                })
                .statelessComponent(con -> {
                    con.setItem(2, 4, ItemBuilder.from(item.clone())
                            .name(Text.toSmallCapsComponent("Item").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                            .lore(
                                    Component.text("Click to select item", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                                    Component.text("(" + PlainTextComponentSerializer.plainText()
                                                    .serialize(item.effectiveName()) + ")", NamedTextColor.GRAY)
                                            .decoration(TextDecoration.ITALIC, false)
                            )
                            .flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> openOrderItemPicker(
                                        player, amount, price, OrderPickerSortType.A_Z, ItemCategory.ALL, ""));
                            }));

                    con.setItem(2, 5, ItemBuilder.from(Material.CHEST)
                            .name(Text.toSmallCapsComponent("Amount").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                            .lore(
                                    Component.text("Click to set amount", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                                    Component.text("(" + Text.formatShortBalance(amount) + " Items)", NamedTextColor.GRAY)
                                            .decoration(TextDecoration.ITALIC, false)
                            )
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> new SignGUI()
                                        .lines(List.of(
                                                Component.text(""),
                                                Component.text("↑↑↑↑↑↑↑↑").color(Colors.HOT_PINK),
                                                Component.text("Enter Amount")))
                                        .material(Material.OAK_WALL_SIGN)
                                        .action(action -> {
                                            long parsed  = (long) Text.parseAmountString(action.line(0));
                                            long clamped = Math.max(1, Math.min(parsed, 1_000_000_000L));
                                            schedule(action.player(), () -> openCreateOrder(action.player(), item, clamped, price));
                                        })
                                        .open(player));
                            }));

                    con.setItem(2, 6, ItemBuilder.from(Material.EMERALD)
                            .name(Text.toSmallCapsComponent("Price Per Item").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                            .lore(
                                    Component.text("Click to set price", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                                    Component.text("($" + Text.formatShortBalance(price) + " Each", NamedTextColor.GRAY)
                                            .decoration(TextDecoration.ITALIC, false)
                            )
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> new SignGUI()
                                        .lines(List.of(
                                                Component.text(""),
                                                Component.text("↑↑↑↑↑↑↑↑").color(Colors.HOT_PINK),
                                                Component.text("Price Per Item")))
                                        .material(Material.OAK_WALL_SIGN)
                                        .action(action -> {
                                            long parsed  = (long) Text.parseAmountString(action.line(0));
                                            long clamped = Math.max(1, Math.min(parsed, 1_000_000_000_000L));
                                            schedule(action.player(), () -> openCreateOrder(action.player(), item, amount, clamped));
                                        })
                                        .open(player));
                            }));

                    con.setItem(2, 8, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Confirm Order").color(TextColor.color(0x00ff00)).decoration(TextDecoration.ITALIC, false))
                            .lore(
                                    Component.text("Click to confirm", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                                    Component.text("(Total $" + Text.formatShortBalance(price * amount) + ")", NamedTextColor.GRAY)
                                            .decoration(TextDecoration.ITALIC, false)
                            )
                            .asGuiItem((player, ctx) -> {
                                long total = price * amount;

                                if (EcoManager.getMoney(player.getUniqueId()) < total) {
                                    OrdersManager.sendError(player, "You can't afford to order this item!");
                                    return;
                                }

                                if (!EcoManager.takeMoney(player.getUniqueId(), total)) {
                                    OrdersManager.sendError(player, "Payment failed!");
                                    return;
                                }

                                OrderListing listing = new OrderListing(
                                        UUID.randomUUID(), player.getUniqueId(), Instant.now(),
                                        item.clone(), amount, 0L, 0L, price);
                                OrdersManager.addListing(listing);

                                navigating[0] = true;
                                player.playSound(player.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1f, 1f);

                                TextComponent broadcast = Component.text(player.getName(), Colors.HOT_PINK)
                                        .append(Component.text(" has ordered ", NamedTextColor.GRAY))
                                        .append(Component.text(Text.formatShortBalance(amount) + " ", Colors.HOT_PINK))
                                        .append(item.effectiveName().color(Colors.HOT_PINK))
                                        .append(Component.text(" for ", NamedTextColor.GRAY))
                                        .append(Component.text("$" + Text.formatShortBalance(price), Colors.HOT_PINK))
                                        .append(Component.text(" each", NamedTextColor.GRAY));

                                PlayerManager.broadcast(broadcast, Sound.BLOCK_NOTE_BLOCK_HAT);
                                schedule(player, () -> buildMyOrdersGui(player, OrdersManager.getListings()));
                            }));

                    con.setItem(2, 2, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Cancel").color(Colors.RED).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to cancel", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> openMyOrders(player));
                            }));
                })
                .build()
                .open(p);
    }

    // ════════════════════════════════════════════════════════════════════════
    // ORDER ITEM PICKER GUI
    // ════════════════════════════════════════════════════════════════════════

    private static void openOrderItemPicker(
            @NotNull Player p, long amount, long price,
            @NotNull OrderPickerSortType sortType, @NotNull ItemCategory category, @NotNull String searchQuery) {

        List<Material> filtered = getPickerFiltered(sortType, category, searchQuery);
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) (9 * 5)));
        final var navigating = new boolean[]{false};

        var sortRef  = new Object() { OrderPickerSortType value = sortType; };
        var catRef   = new Object() { ItemCategory value = category; };
        var queryRef = new Object() { String value = searchQuery; };

        Gui.of(6)
                .title(Text.toSmallCapsComponent("Select Item"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0]) schedule(p, () -> openCreateOrder(p, new ItemStack(Material.STONE), amount, price));
                })
                .component(component -> {
                    PagerState<Material> pageState = PagerState.of(
                            filtered, GuiLayout.box(Slot.of(1, 1), Slot.of(5, 9)));
                    component.remember(pageState);

                    component.render(con -> {
                        pageState.forEach(entry -> {
                            Material  mat   = entry.element();
                            ItemStack stack = new ItemStack(mat);

                            List<Component> lore = new ArrayList<>();
                            if (stack.hasItemMeta() && stack.getItemMeta().hasLore() && stack.getItemMeta().lore() != null) {
                                lore.addAll(stack.getItemMeta().lore());
                                lore.add(Component.empty());
                            }
                            lore.add(Component.text("(Click to Select)").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

                            con.setItem(entry.slot(), ItemBuilder.from(stack)
                                    .lore(lore)
                                    .flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
                                    .asGuiItem((player, ctx) -> {
                                        player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                        List<Enchantment> enchants = ItemUtils.getCompatibleEnchantments(stack);
                                        if (enchants.isEmpty()) {
                                            navigating[0] = true;
                                            schedule(player, () -> openCreateOrder(player, new ItemStack(mat), amount, price));
                                        } else {
                                            navigating[0] = true;
                                            schedule(player, () -> openOrderItemEnchantEditor(
                                                    player, new ItemStack(mat), enchants, amount, price));
                                        }
                                    }));
                        });

                        int page = pageState.getCurrentPage();
                        if (page > 1)          con.setItem(6, 1, prevButton(p, pageState));
                        if (page < totalPages) con.setItem(6, 9, nextButton(p, pageState));

                        con.setItem(6, 4, ItemBuilder.from(Material.CAULDRON)
                                .name(Text.toSmallCapsComponent("Sort").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Arrays.stream(OrderPickerSortType.values())
                                        .map(s -> (Component) Text.toSmallCapsComponent("▪ " + s)
                                                .color(sortRef.value == s ? Colors.HOT_PINK : NamedTextColor.WHITE)
                                                .decoration(TextDecoration.ITALIC, false))
                                        .collect(Collectors.toList()))
                                .asGuiItem((player, ctx) -> {
                                    sortRef.value = OrderPickerSortType.next(sortRef.value);
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    navigating[0] = true;
                                    schedule(player, () -> openOrderItemPicker(player, amount, price, sortRef.value, catRef.value, queryRef.value));
                                }));

                        con.setItem(6, 5, ItemBuilder.from(Material.HOPPER)
                                .name(Text.toSmallCapsComponent("Filter").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Arrays.stream(ItemCategory.values())
                                        .map(c -> (Component) Text.toSmallCapsComponent("▪ " + c)
                                                .color(catRef.value == c ? Colors.HOT_PINK : NamedTextColor.WHITE)
                                                .decoration(TextDecoration.ITALIC, false))
                                        .collect(Collectors.toList()))
                                .asGuiItem((player, ctx) -> {
                                    ItemCategory[] cats = ItemCategory.values();
                                    catRef.value = cats[(catRef.value.ordinal() + 1) % cats.length];
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    navigating[0] = true;
                                    schedule(player, () -> openOrderItemPicker(player, amount, price, sortRef.value, catRef.value, queryRef.value));
                                }));

                        con.setItem(6, 6, ItemBuilder.from(Material.OAK_SIGN)
                                .name(Text.toSmallCapsComponent("Search").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to search").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    schedule(player, () -> new SignGUI()
                                            .lines(List.of(
                                                    Component.text(""),
                                                    Component.text("↑↑↑↑↑↑↑↑").color(Colors.HOT_PINK),
                                                    Component.text("Search")))
                                            .material(Material.OAK_WALL_SIGN)
                                            .action(action -> {
                                                queryRef.value = action.line(0);
                                                schedule(action.player(), () -> openOrderItemPicker(
                                                        action.player(), amount, price, sortRef.value, catRef.value, queryRef.value));
                                            })
                                            .open(player));
                                }));
                    });
                })
                .build()
                .open(p);
    }

    private static void openOrderItemEnchantEditor(
            @NotNull Player p, @NotNull ItemStack item, @NotNull List<Enchantment> enchants,
            long amount, long price) {
        final var navigating = new boolean[]{false};

        Gui.of(3)
                .title(Text.toSmallCapsComponent("Enchantments Editor"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0]) schedule(p, () -> openOrderItemPicker(
                            p, amount, price, OrderPickerSortType.A_Z, ItemCategory.ALL, ""));
                })
                .statelessComponent(con -> {
                    con.setItem(2, 5, ItemBuilder.from(item).asGuiItem());

                    con.setItem(2, 2, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Cancel").color(Colors.RED).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to cancel").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> openOrderItemPicker(
                                        player, amount, price, OrderPickerSortType.A_Z, ItemCategory.ALL, ""));
                            }));

                    con.setItem(2, 4, ItemBuilder.from(Material.BOOK)
                            .name(Text.toSmallCapsComponent("Clear Enchantments").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to clear all enchantments").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                ItemStack cleared = item.clone();
                                for (Enchantment e : new ArrayList<>(cleared.getEnchantments().keySet())) cleared.removeEnchantment(e);
                                schedule(player, () -> openOrderItemEnchantEditor(player, cleared, enchants, amount, price));
                            }));

                    con.setItem(2, 6, ItemBuilder.from(Material.ENCHANTED_BOOK)
                            .name(Text.toSmallCapsComponent("Pick Enchantments").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to choose an enchantment").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> openOrderEnchantPicker(player, item, item.clone(), enchants, 0, amount, price));
                            }));

                    con.setItem(2, 8, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Confirm").color(TextColor.color(0x00ff00)).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to confirm enchantments").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> openCreateOrder(player, item, amount, price));
                            }));
                })
                .build()
                .open(p);
    }

    private static void openOrderEnchantPicker(
            @NotNull Player p, @NotNull ItemStack original, @NotNull ItemStack current,
            @NotNull List<Enchantment> enchants, int scrollIndex, long amount, long price) {
        final var navigating = new boolean[]{false};

        Gui.of(3)
                .title(Text.toSmallCapsComponent("Pick Enchantments"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0]) schedule(p, () -> openOrderItemEnchantEditor(p, original, enchants, amount, price));
                })
                .statelessComponent(con -> {
                    for (int i = 0; i < 9; i++) {
                        int col  = i + 1;
                        boolean edge = col == 1 || col == 9;

                        con.setItem(1, col, ItemBuilder.from(edge ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                                .name(Component.empty()).asGuiItem());
                        con.setItem(3, col, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                                .name(Component.empty()).asGuiItem());

                        if (!edge) {
                            int idx      = ((scrollIndex + (col - 2)) % enchants.size() + enchants.size()) % enchants.size();
                            Enchantment ench = enchants.get(idx);
                            int curLevel = current.getEnchantmentLevel(ench);
                            int maxLevel = ench.getMaxLevel();
                            boolean active = current.containsEnchantment(ench);

                            String loreLine = curLevel == 0 ? "(Click To Add)" : curLevel < maxLevel ? "(Click To Upgrade)" : "(Click To Remove)";

                            con.setItem(2, col, ItemBuilder.from(Material.ENCHANTED_BOOK)
                                    .name((active
                                            ? Text.toSmallCapsComponent("Active").color(Colors.GREEN)
                                            : Text.toSmallCapsComponent("Inactive").color(Colors.RED))
                                            .decoration(TextDecoration.ITALIC, false))
                                    .lore(Component.text(loreLine).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                    .enchant(ench, Math.max(1, curLevel))
                                    .flags(ItemFlag.HIDE_STORED_ENCHANTS)
                                    .asGuiItem((player, ctx) -> {
                                        navigating[0] = true;
                                        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                                        ItemStack next = current.clone();
                                        if (active && curLevel >= maxLevel) {
                                            next.removeEnchantment(ench);
                                        } else {
                                            for (Enchantment ex : new ArrayList<>(next.getEnchantments().keySet())) {
                                                if (ench.conflictsWith(ex)) next.removeEnchantment(ex);
                                            }
                                            next.addUnsafeEnchantment(ench, curLevel + 1);
                                        }
                                        schedule(player, () -> openOrderEnchantPicker(player, original, next, enchants, scrollIndex, amount, price));
                                    }));
                        }
                    }

                    con.setItem(2, 1, ItemBuilder.from(Material.ARROW)
                            .name(Text.toSmallCapsComponent("Previous").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Scroll enchantments").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                                ItemStack snap = current.clone();
                                schedule(player, () -> openOrderEnchantPicker(player, original, snap, enchants,
                                        (scrollIndex - 1 + enchants.size()) % enchants.size(), amount, price));
                            }));

                    con.setItem(2, 9, ItemBuilder.from(Material.ARROW)
                            .name(Text.toSmallCapsComponent("Next").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Scroll enchantments").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                                ItemStack snap = current.clone();
                                schedule(player, () -> openOrderEnchantPicker(player, original, snap, enchants,
                                        (scrollIndex + 1) % enchants.size(), amount, price));
                            }));

                    con.setItem(3, 1, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Cancel").color(Colors.RED).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to cancel enchantment changes").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> openOrderItemEnchantEditor(player, original, enchants, amount, price));
                            }));

                    con.setItem(3, 9, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Confirm").color(TextColor.color(0x00ff00)).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to update enchantments").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                ItemStack snap = current.clone();
                                schedule(player, () -> openOrderItemEnchantEditor(player, snap, enchants, amount, price));
                            }));
                })
                .build()
                .open(p);
    }

    // ════════════════════════════════════════════════════════════════════════
    // TRANSACTIONS GUI
    // ════════════════════════════════════════════════════════════════════════

    public static void openTransactions(@NotNull Player p, @NotNull UUID target, @NotNull String query) {
        buildTransactionsGui(p, target, OrdersManager.getPlayerTransactions(target), query);
    }

    private static void buildTransactionsGui(
            @NotNull Player p, @NotNull UUID target,
            @NotNull List<OrderTransaction> snapshot, @NotNull String query) {
        List<OrderTransaction> filtered = filterTx(snapshot, query);

        int rows       = 6;
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / 45.0));
        final var navigating = new boolean[]{false};

        Gui.of(rows)
                .title(Text.toSmallCapsComponent("Orders \u2192 History"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0]) schedule(p, () -> openMyOrders(p));
                })
                .component(component -> {
                    PagerState<OrderTransaction> pageState = PagerState.of(
                            filtered, GuiLayout.box(Slot.of(1, 1), Slot.of(5, 9)));
                    component.remember(pageState);

                    component.render(con -> {
                        pageState.forEach(entry ->
                                con.setItem(entry.slot(), buildTxItem(p, target, entry.element())));

                        int page = pageState.getCurrentPage();
                        if (page > 1)          con.setItem(6, 1, prevButton(p, pageState));
                        if (page < totalPages) con.setItem(6, 9, nextButton(p, pageState));

                        con.setItem(6, 5, ItemBuilder.from(Material.MAP)
                                .name(Text.toSmallCapsComponent("Transactions").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to refresh", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_TOAST_OUT, 1f, 1f);
                                    schedule(player, () -> buildTransactionsGui(
                                            player, target, OrdersManager.getPlayerTransactions(target), query));
                                }));

                        con.setItem(6, 6, ItemBuilder.from(Material.OAK_SIGN)
                                .name(Text.toSmallCapsComponent("Search").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to search", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    schedule(player, () -> new SignGUI()
                                            .lines(List.of(
                                                    Component.text(""),
                                                    Component.text("↑↑↑↑↑↑↑↑").color(Colors.HOT_PINK),
                                                    Component.text("Search")))
                                            .material(Material.OAK_WALL_SIGN)
                                            .action(action -> schedule(action.player(),
                                                    () -> openTransactions(action.player(), target, action.line(0))))
                                            .open(player));
                                }));
                    });
                })
                .build()
                .open(p);
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    public static void cleanup(@NotNull UUID playerId) {
        BROWSE_STATES.remove(playerId);
    }
}