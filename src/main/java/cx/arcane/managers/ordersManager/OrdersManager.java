package cx.arcane.managers.ordersManager;

import cx.arcane.Arcane;
import cx.arcane.managers.dbManager.DBManager;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import cx.arcane.utils.Text;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class OrdersManager {

    private OrdersManager() {}

    private static final ConcurrentHashMap<UUID, OrderListing>           LISTINGS           = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<OrderTransaction>           TRANSACTIONS       = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<UUID, List<OrderTransaction>>  OFFLINE_DELIVERIES = new ConcurrentHashMap<>();

    private static final NamespacedKey ORDER_LISTING_KEY =
            new NamespacedKey(Arcane.getPlugin(), "order_listing_id");

    public static NamespacedKey getOrderListingKey() { return ORDER_LISTING_KEY; }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public static void onEnable() {
        createTables();
        loadAll();
    }

    public static void onDisable() {
        saveAll();
    }

    public static void onSave() {
        createTables();
        saveAll();
    }

    public static void createTables() {
        try (Connection con = DBManager.getConnection(); Statement st = con.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS acx_orders (
                    Id VARCHAR(36) NOT NULL,
                    OwnerId VARCHAR(36) NOT NULL,
                    ListedAt BIGINT NOT NULL,
                    Amount BIGINT NOT NULL,
                    Delivered BIGINT NOT NULL,
                    Collected BIGINT NOT NULL,
                    Price BIGINT NOT NULL,
                    Item MEDIUMBLOB NOT NULL,
                    PRIMARY KEY (Id)
                ) ENGINE=InnoDB
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS acx_orders_history (
                    Id VARCHAR(36) NOT NULL,
                    TransactedAt BIGINT NOT NULL,
                    BuyerId VARCHAR(36) NOT NULL,
                    SellerId VARCHAR(36) NOT NULL,
                    Amount BIGINT NOT NULL,
                    Price BIGINT NOT NULL,
                    Item MEDIUMBLOB NOT NULL,
                    PRIMARY KEY (Id)
                ) ENGINE=InnoDB
            """);
        } catch (SQLException e) {
            Log.error("[OrdersManager] Table creation failed");
            e.printStackTrace();
        }
    }

    public static void loadAll() {
        LISTINGS.clear();
        TRANSACTIONS.clear();

        try (Connection con = DBManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM acx_orders");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID      id        = UUID.fromString(rs.getString("Id"));
                    UUID      owner     = UUID.fromString(rs.getString("OwnerId"));
                    Instant   at        = Instant.ofEpochMilli(rs.getLong("ListedAt"));
                    long      amount    = rs.getLong("Amount");
                    long      delivered = rs.getLong("Delivered");
                    long      collected = rs.getLong("Collected");
                    long      price     = rs.getLong("Price");
                    ItemStack item      = ItemStack.deserializeBytes(rs.getBytes("Item"));
                    LISTINGS.put(id, new OrderListing(id, owner, at, item, amount, delivered, collected, price));
                }
            }

            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM acx_orders_history");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID      id     = UUID.fromString(rs.getString("Id"));
                    Instant   at     = Instant.ofEpochMilli(rs.getLong("TransactedAt"));
                    UUID      buyer  = UUID.fromString(rs.getString("BuyerId"));
                    UUID      seller = UUID.fromString(rs.getString("SellerId"));
                    long      amount = rs.getLong("Amount");
                    long      price  = rs.getLong("Price");
                    ItemStack item   = ItemStack.deserializeBytes(rs.getBytes("Item"));
                    TRANSACTIONS.add(new OrderTransaction(id, at, item, amount, price, buyer, seller));
                }
            }

            Log.info("[OrdersManager] Loaded {} listings and {} history records.", LISTINGS.size(), TRANSACTIONS.size());
        } catch (Exception e) {
            Log.error("[OrdersManager] loadAll() failed");
            e.printStackTrace();
        }
    }

    public static void saveAll() {
        long t = System.currentTimeMillis();

        String listingSql = """
            INSERT INTO acx_orders (Id, OwnerId, ListedAt, Amount, Delivered, Collected, Price, Item)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                Delivered=VALUES(Delivered), Collected=VALUES(Collected),
                Price=VALUES(Price), Item=VALUES(Item)
        """;

        String txSql = """
            INSERT IGNORE INTO acx_orders_history (Id, TransactedAt, BuyerId, SellerId, Amount, Price, Item)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection con = DBManager.getConnection()) {
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(listingSql)) {
                for (OrderListing l : LISTINGS.values()) {
                    ps.setString(1, l.getId().toString());
                    ps.setString(2, l.getOwnerId().toString());
                    ps.setLong(3, l.getListedAt().toEpochMilli());
                    ps.setLong(4, l.getAmount());
                    ps.setLong(5, l.getDeliveredAmount());
                    ps.setLong(6, l.getCollectedAmount());
                    ps.setLong(7, l.getPrice());
                    ps.setBytes(8, l.getItem().serializeAsBytes());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (Statement st = con.createStatement()) {
                if (!LISTINGS.isEmpty()) {
                    String ids = LISTINGS.keySet().stream()
                            .map(u -> "'" + u + "'")
                            .collect(java.util.stream.Collectors.joining(","));
                    st.executeUpdate("DELETE FROM acx_orders WHERE Id NOT IN (" + ids + ")");
                } else {
                    st.executeUpdate("DELETE FROM acx_orders");
                }
            }

            try (PreparedStatement ps = con.prepareStatement(txSql)) {
                for (OrderTransaction tx : TRANSACTIONS) {
                    ps.setString(1, tx.getId().toString());
                    ps.setLong(2, tx.getTransactedAt().toEpochMilli());
                    ps.setString(3, tx.getBuyerId().toString());
                    ps.setString(4, tx.getSellerId().toString());
                    ps.setLong(5, tx.getAmount());
                    ps.setLong(6, tx.getPrice());
                    ps.setBytes(7, tx.getItem().serializeAsBytes());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            con.commit();
            Log.info("[OrdersManager] saveAll() completed in {}ms", System.currentTimeMillis() - t);
        } catch (Exception e) {
            Log.error("[OrdersManager] saveAll() failed");
            e.printStackTrace();
        }
    }

    // ── Listing queries ──────────────────────────────────────────────────────

    public static List<OrderListing> getListings() {
        return List.copyOf(LISTINGS.values());
    }

    @Nullable
    public static OrderListing getListing(@NotNull UUID id) {
        return LISTINGS.get(id);
    }

    public static boolean hasListing(@NotNull UUID id) {
        return LISTINGS.containsKey(id);
    }

    public static boolean ownsListing(@NotNull UUID playerId, @NotNull UUID listingId) {
        OrderListing l = LISTINGS.get(listingId);
        return l != null && l.getOwnerId().equals(playerId);
    }

    public static void addListing(@NotNull OrderListing listing) {
        LISTINGS.put(listing.getId(), listing);
    }

    public static boolean removeListing(@NotNull UUID listingId) {
        return LISTINGS.remove(listingId) != null;
    }

    public static int listingsCount(@NotNull Player p) {
        return (int) LISTINGS.values().stream()
                .filter(l -> l.getOwnerId().equals(p.getUniqueId()))
                .count();
    }

    // ── Transaction queries ──────────────────────────────────────────────────

    public static List<OrderTransaction> getTransactions() {
        return Collections.unmodifiableList(TRANSACTIONS);
    }

    public static List<OrderTransaction> getPlayerTransactions(@NotNull UUID playerId) {
        return TRANSACTIONS.stream()
                .filter(t -> t.getBuyerId().equals(playerId) || t.getSellerId().equals(playerId))
                .toList();
    }

    public static void logTransaction(@NotNull OrderTransaction tx) {
        TRANSACTIONS.add(tx);
    }

    // ── Offline deliveries ───────────────────────────────────────────────────

    public static void logOfflineDelivery(@NotNull OrderTransaction tx) {
        OFFLINE_DELIVERIES.computeIfAbsent(tx.getBuyerId(), k -> new CopyOnWriteArrayList<>()).add(tx);
    }

    public static boolean hasOfflineDelivery(@NotNull UUID playerId) {
        return OFFLINE_DELIVERIES.containsKey(playerId);
    }

    public static int countDeliveriesWhileOffline(@NotNull UUID playerId) {
        return OFFLINE_DELIVERIES.getOrDefault(playerId, Collections.emptyList()).size();
    }

    public static long getTotalDeliveredWhileOffline(@NotNull UUID playerId) {
        return OFFLINE_DELIVERIES.getOrDefault(playerId, Collections.emptyList())
                .stream().mapToLong(OrderTransaction::getAmount).sum();
    }

    public static void consumeOfflineDeliveries(@NotNull UUID playerId) {
        OFFLINE_DELIVERIES.remove(playerId);
    }

    public static void onLogin(@NotNull Player p) {
        if (!hasOfflineDelivery(p.getUniqueId())) return;

        FoliaScheduler.getEntityScheduler().runDelayed(p, Arcane.getPlugin(), task -> {
            UUID id   = p.getUniqueId();
            int count = countDeliveriesWhileOffline(id);
            if (count <= 0) return;

            TextComponent message;
            if (count == 1) {
                List<OrderTransaction> txs = OFFLINE_DELIVERIES.get(id);
                OrderTransaction tx = txs != null ? txs.getFirst() : null;
                if (tx == null) return;
                message = Component.text(Text.formatShortBalance(tx.getAmount()) + " ", Colors.HOT_PINK)
                        .append(tx.getItem().effectiveName().color(Colors.HOT_PINK))
                        .append(Component.text(" was delivered to your order while you were away!", NamedTextColor.GRAY));
            } else {
                long total = getTotalDeliveredWhileOffline(id);
                message = Component.text(Text.formatShortBalance(total) + " items", Colors.HOT_PINK)
                        .append(Component.text(" were delivered to your orders while you were away!", NamedTextColor.GRAY));
            }

            p.sendMessage(message);
            p.sendActionBar(message);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1f);
            consumeOfflineDeliveries(id);
        }, null, 40L);
    }

    // ── Delivery result ──────────────────────────────────────────────────────

    public enum DeliverResult {
        SUCCESS, NOT_FOUND, FULFILLED, NOTHING_TO_DELIVER
    }

    public static final class DeliveryOutcome {
        public final DeliverResult  result;
        public final long           delivered;
        public final List<ItemStack> rejected;

        DeliveryOutcome(@NotNull DeliverResult result, long delivered, @NotNull List<ItemStack> rejected) {
            this.result    = result;
            this.delivered = delivered;
            this.rejected  = rejected;
        }
    }

    // ── Core delivery logic ──────────────────────────────────────────────────

    /**
     * Atomically delivers items from inv against a listing.
     * Safe to call from any thread/region concurrently.
     * Shulker boxes and bundles are inspected; matching items extracted,
     * and the modified container returned in rejected with remaining non-matching contents.
     */
    public static DeliveryOutcome deliverOrder(@NotNull Inventory inv, @NotNull UUID listingId) {
        OrderListing listing = LISTINGS.get(listingId);
        if (listing == null) return new DeliveryOutcome(DeliverResult.NOT_FOUND, 0, collectAll(inv));
        if (listing.isFulfilled()) return new DeliveryOutcome(DeliverResult.FULFILLED, 0, collectAll(inv));

        synchronized (listing) {
            long remaining = listing.getAmount() - listing.getDeliveredAmount();
            if (remaining <= 0) return new DeliveryOutcome(DeliverResult.FULFILLED, 0, collectAll(inv));

            List<ItemStack> rejected = new ArrayList<>();
            long delivered = 0;

            for (ItemStack stack : inv.getContents()) {
                if (stack == null || stack.getType().isAir()) continue;

                if (delivered >= remaining) {
                    rejected.add(stack.clone());
                    continue;
                }

                if (stack.isSimilar(listing.getItem())) {
                    int take = (int) Math.min(stack.getAmount(), remaining - delivered);
                    delivered += take;
                    listing.increaseDeliveredAmount(take);
                    if (take < stack.getAmount()) {
                        ItemStack leftover = stack.clone();
                        leftover.setAmount(stack.getAmount() - take);
                        rejected.add(leftover);
                    }
                } else if (isShulker(stack)) {
                    ItemStack result = extractFromShulker(stack, listing.getItem(), remaining - delivered);
                    long took = result == null ? 0 : countExtracted(stack, result);
                    if (took > 0) {
                        delivered += took;
                        listing.increaseDeliveredAmount(took);
                        rejected.add(result);
                    } else {
                        rejected.add(stack.clone());
                    }
                } else if (isBundle(stack)) {
                    ItemStack result = extractFromBundle(stack, listing.getItem(), remaining - delivered);
                    long took = result == null ? 0 : countBundleExtracted(stack, result, listing.getItem());
                    if (took > 0) {
                        delivered += took;
                        listing.increaseDeliveredAmount(took);
                        rejected.add(result);
                    } else {
                        rejected.add(stack.clone());
                    }
                } else {
                    rejected.add(stack.clone());
                }
            }

            if (delivered == 0) return new DeliveryOutcome(DeliverResult.NOTHING_TO_DELIVER, 0, rejected);
            return new DeliveryOutcome(DeliverResult.SUCCESS, delivered, rejected);
        }
    }

    public static long countDeliverableItems(@NotNull Inventory inv, @NotNull OrderListing listing) {
        long remaining = listing.getAmount() - listing.getDeliveredAmount();
        if (remaining <= 0) return 0;
        long count = 0;

        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType().isAir()) continue;

            if (stack.isSimilar(listing.getItem())) {
                count += stack.getAmount();
            } else if (isShulker(stack)) {
                if (!(stack.getItemMeta() instanceof BlockStateMeta bsm)) continue;
                if (!(bsm.getBlockState() instanceof ShulkerBox box)) continue;
                for (ItemStack inner : box.getInventory().getContents()) {
                    if (inner != null && !inner.getType().isAir() && inner.isSimilar(listing.getItem()))
                        count += inner.getAmount();
                }
            } else if (isBundle(stack)) {
                if (!(stack.getItemMeta() instanceof BundleMeta bm)) continue;
                for (ItemStack inner : bm.getItems()) {
                    if (inner != null && !inner.getType().isAir() && inner.isSimilar(listing.getItem()))
                        count += inner.getAmount();
                }
            }

            if (count >= remaining) return remaining;
        }

        return Math.min(count, remaining);
    }

    /**
     * High-level delivery handler: delivers, pays seller, notifies owner, logs transaction.
     * Calls back on the calling thread — schedule player interactions externally.
     * ownerNotifier runs on the owner's entity scheduler if online, else logs offline delivery.
     */
    public static DeliveryOutcome processDelivery(
            @NotNull Inventory inv,
            @NotNull UUID listingId,
            @NotNull UUID sellerId,
            @NotNull Consumer<DeliveryOutcome> onResult) {

        DeliveryOutcome outcome = deliverOrder(inv, listingId);
        onResult.accept(outcome);

        if (outcome.result != DeliverResult.SUCCESS) return outcome;

        OrderListing listing = LISTINGS.get(listingId);
        if (listing == null) return outcome;

        long earned = outcome.delivered * listing.getPrice();
        EcoManager.giveMoney(sellerId, earned);

        OrderTransaction tx = new OrderTransaction(
                Instant.now(), listing.getItem(),
                outcome.delivered, listing.getPrice(),
                listing.getOwnerId(), sellerId);
        logTransaction(tx);

        Player owner = PlayerManager.getPlayer(listing.getOwnerId());
        String sellerName = PlayerManager.getName(sellerId);

        if (owner != null) {
            OrderListing finalListing = listing;
            FoliaScheduler.getEntityScheduler().execute(owner, Arcane.getPlugin(), () -> {
                TextComponent msg = Component.text(sellerName != null ? sellerName : "Someone", Colors.HOT_PINK)
                        .append(Component.text(" delivered ", NamedTextColor.GRAY))
                        .append(Component.text(Text.formatShortBalance(outcome.delivered) + " ", Colors.HOT_PINK))
                        .append(finalListing.getItem().effectiveName().color(Colors.HOT_PINK))
                        .append(Component.text(" to your order!", NamedTextColor.GRAY));
                owner.sendMessage(msg);
                owner.sendActionBar(msg);
                owner.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }, null, 0L);
        } else {
            logOfflineDelivery(tx);
        }

        return outcome;
    }

    // ── Shulker / bundle helpers ─────────────────────────────────────────────

    public static boolean isShulker(@NotNull ItemStack stack) {
        return stack.getType().name().contains("SHULKER_BOX");
    }

    public static boolean isBundle(@NotNull ItemStack stack) {
        return stack.getType() == org.bukkit.Material.BUNDLE;
    }

    /**
     * Returns a modified clone of the shulker with up to maxTake matching items removed.
     * Returns null if meta is invalid.
     */
    @Nullable
    private static ItemStack extractFromShulker(
            @NotNull ItemStack shulker, @NotNull ItemStack target, long maxTake) {
        if (!(shulker.getItemMeta() instanceof BlockStateMeta bsm)) return null;
        if (!(bsm.getBlockState() instanceof ShulkerBox box)) return null;

        Inventory shulkerInv = box.getInventory();
        boolean modified = false;

        for (int i = 0; i < shulkerInv.getSize() && maxTake > 0; i++) {
            ItemStack inner = shulkerInv.getItem(i);
            if (inner == null || inner.getType().isAir() || !inner.isSimilar(target)) continue;

            int take = (int) Math.min(inner.getAmount(), maxTake);
            maxTake -= take;
            if (take < inner.getAmount()) inner.setAmount(inner.getAmount() - take);
            else shulkerInv.setItem(i, null);
            modified = true;
        }

        if (!modified) return null;

        box.getInventory().setContents(shulkerInv.getContents());
        bsm.setBlockState(box);
        ItemStack result = shulker.clone();
        result.setItemMeta(bsm);
        return result;
    }

    /**
     * Returns a modified clone of the bundle with up to maxTake matching items removed.
     * Returns null if meta is invalid.
     */
    @Nullable
    private static ItemStack extractFromBundle(
            @NotNull ItemStack bundle, @NotNull ItemStack target, long maxTake) {
        if (!(bundle.getItemMeta() instanceof BundleMeta bm)) return null;

        List<ItemStack> contents = new ArrayList<>(bm.getItems());
        boolean modified = false;

        for (int i = 0; i < contents.size() && maxTake > 0; i++) {
            ItemStack inner = contents.get(i);
            if (inner == null || inner.getType().isAir() || !inner.isSimilar(target)) continue;

            int take = (int) Math.min(inner.getAmount(), maxTake);
            maxTake -= take;
            if (take < inner.getAmount()) {
                inner = inner.clone();
                inner.setAmount(inner.getAmount() - take);
                contents.set(i, inner);
            } else {
                contents.remove(i);
                i--;
            }
            modified = true;
        }

        if (!modified) return null;

        BundleMeta newMeta = (BundleMeta) bm.clone();
        newMeta.setItems(contents);
        ItemStack result = bundle.clone();
        result.setItemMeta(newMeta);
        return result;
    }

    private static long countExtracted(@NotNull ItemStack original, @NotNull ItemStack modified) {
        long before = 0, after = 0;
        if (original.getItemMeta() instanceof BlockStateMeta bsmO &&
                bsmO.getBlockState() instanceof ShulkerBox boxO) {
            for (ItemStack s : boxO.getInventory().getContents())
                if (s != null && !s.getType().isAir()) before += s.getAmount();
        }
        if (modified.getItemMeta() instanceof BlockStateMeta bsmM &&
                bsmM.getBlockState() instanceof ShulkerBox boxM) {
            for (ItemStack s : boxM.getInventory().getContents())
                if (s != null && !s.getType().isAir()) after += s.getAmount();
        }
        return before - after;
    }

    private static long countBundleExtracted(
            @NotNull ItemStack original, @NotNull ItemStack modified, @NotNull ItemStack target) {
        long before = 0, after = 0;
        if (original.getItemMeta() instanceof BundleMeta bmO) {
            for (ItemStack s : bmO.getItems())
                if (s != null && s.isSimilar(target)) before += s.getAmount();
        }
        if (modified.getItemMeta() instanceof BundleMeta bmM) {
            for (ItemStack s : bmM.getItems())
                if (s != null && s.isSimilar(target)) after += s.getAmount();
        }
        return before - after;
    }

    // ── Item return ──────────────────────────────────────────────────────────

    public static void returnItem(@NotNull Player p, @NotNull ItemStack item) {
        if (item.getType().isAir()) return;
        Map<Integer, ItemStack> leftovers = p.getInventory().addItem(item.clone());
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(s -> p.getWorld().dropItemNaturally(p.getLocation(), s));
            FoliaScheduler.getEntityScheduler().execute(p, Arcane.getPlugin(), () ->
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f), null, 0L);
        }
    }

    public static void returnItems(@NotNull Player p, @NotNull List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) returnItem(p, item);
        }
    }

    public static void returnInventory(@NotNull Player p, @NotNull Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && !item.getType().isAir()) returnItem(p, item);
        }
    }

    // ── Inventory utils ──────────────────────────────────────────────────────

    public static boolean isInventoryFull(@NotNull Inventory inv) {
        for (ItemStack s : inv.getContents()) {
            if (s == null || s.getType().isAir()) return false;
        }
        return true;
    }

    public static long countItems(@NotNull Inventory inv) {
        long count = 0;
        for (ItemStack s : inv.getContents()) {
            if (s != null && !s.getType().isAir()) count += s.getAmount();
        }
        return count;
    }

    private static List<ItemStack> collectAll(@NotNull Inventory inv) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack s : inv.getContents()) {
            if (s != null && !s.getType().isAir()) result.add(s.clone());
        }
        return result;
    }

    // ── Messaging ────────────────────────────────────────────────────────────

    public static void sendError(@NotNull Player p, @NotNull String message) {
        TextComponent msg = Component.text(message, Colors.RED);
        p.sendMessage(msg);
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    public static void sendSuccess(@NotNull Player p, @NotNull String message) {
        TextComponent msg = Component.text(message, Colors.HOT_PINK);
        p.sendMessage(msg);
        p.sendActionBar(msg);
    }
}