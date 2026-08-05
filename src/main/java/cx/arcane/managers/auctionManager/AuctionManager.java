package cx.arcane.managers.auctionManager;

import cx.arcane.Arcane;
import cx.arcane.managers.dbManager.DBManager;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.priceManager.PriceManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.ItemUtils;
import cx.arcane.utils.Log;
import cx.arcane.utils.Text;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AuctionManager {

    private AuctionManager() {}

    private static final ConcurrentHashMap<UUID, AuctionListing> LISTINGS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<AuctionTransaction> TRANSACTIONS = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<UUID, List<AuctionTransaction>> OFFLINE_SALES = new ConcurrentHashMap<>();

    private static final NamespacedKey LISTING_KEY =
            new NamespacedKey(Arcane.getPlugin(), "auction_listing_id");

    public static NamespacedKey getListingKey() { return LISTING_KEY; }

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

    // ── Persistence stubs ────────────────────────────────────────────────────
    public static void createTables() {
        try (Connection con = DBManager.getConnection(); Statement st = con.createStatement()) {
            st.execute("""
            CREATE TABLE IF NOT EXISTS acx_auction (
                Id VARCHAR(36) NOT NULL, 
                OwnerId VARCHAR(36) NOT NULL, 
                ListedAt BIGINT NOT NULL, 
                Price BIGINT NOT NULL, 
                Item BLOB NOT NULL, 
                PRIMARY KEY (Id)
            ) ENGINE=InnoDB
        """);
            st.execute("""
            CREATE TABLE IF NOT EXISTS acx_auction_history (
                Id VARCHAR(36) NOT NULL, 
                TransactedAt BIGINT NOT NULL, 
                BuyerId VARCHAR(36) NOT NULL, 
                SellerId VARCHAR(36) NOT NULL, 
                Price BIGINT NOT NULL, 
                Item BLOB NOT NULL, 
                PRIMARY KEY (Id)
            ) ENGINE=InnoDB
        """);
        } catch (SQLException e) {
            Log.error("[AuctionManager] Table creation failed");
            e.printStackTrace();
        }
    }

    public static void loadAll() {
        LISTINGS.clear();
        TRANSACTIONS.clear();

        try (Connection con = DBManager.getConnection()) {
            // Load Listings
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM acx_auction");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("Id"));
                    UUID owner = UUID.fromString(rs.getString("OwnerId"));
                    Instant listedAt = Instant.ofEpochMilli(rs.getLong("ListedAt"));
                    long price = rs.getLong("Price");
                    ItemStack item = ItemStack.deserializeBytes(rs.getBytes("Item"));

                    LISTINGS.put(id, new AuctionListing(id, owner, listedAt, item, price));
                }
            }

            // Load Transactions
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM acx_auction_history");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("Id"));
                    Instant at = Instant.ofEpochMilli(rs.getLong("TransactedAt"));
                    UUID buyer = UUID.fromString(rs.getString("BuyerId"));
                    UUID seller = UUID.fromString(rs.getString("SellerId"));
                    long price = rs.getLong("Price");
                    ItemStack item = ItemStack.deserializeBytes(rs.getBytes("Item"));

                    TRANSACTIONS.add(new AuctionTransaction(id, at, item, price, buyer, seller));
                }
            }
            Log.info("[AuctionManager] Loaded {} listings and {} history records.", LISTINGS.size(), TRANSACTIONS.size());
        } catch (Exception e) {
            Log.error("[AuctionManager] loadAll() failed");
            e.printStackTrace();
        }
    }

    public static void saveAll() {
        long t = System.currentTimeMillis();

        String listingSql = """
        INSERT INTO acx_auction (Id, OwnerId, ListedAt, Price, Item) 
        VALUES (?, ?, ?, ?, ?) 
        ON DUPLICATE KEY UPDATE OwnerId=VALUES(OwnerId), Price=VALUES(Price), Item=VALUES(Item)
    """;

        String transSql = """
        INSERT IGNORE INTO acx_auction_history (Id, TransactedAt, BuyerId, SellerId, Price, Item) 
        VALUES (?, ?, ?, ?, ?, ?)
    """;

        try (Connection con = DBManager.getConnection()) {
            con.setAutoCommit(false);

            // 1. Batch Upsert Listings
            try (PreparedStatement ps = con.prepareStatement(listingSql)) {
                for (AuctionListing listing : LISTINGS.values()) {
                    ps.setString(1, listing.getId().toString());
                    ps.setString(2, listing.getOwnerId().toString());
                    ps.setLong(3, listing.getListedAt().toEpochMilli());
                    ps.setLong(4, listing.getPrice());
                    ps.setBytes(5, listing.getItem().serializeAsBytes());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // 2. Batch Delete Listings not in memory
            try (Statement st = con.createStatement()) {
                if (!LISTINGS.isEmpty()) {
                    String ids = LISTINGS.keySet().stream()
                            .map(uuid -> "'" + uuid.toString() + "'")
                            .collect(java.util.stream.Collectors.joining(","));
                    st.executeUpdate("DELETE FROM acx_auction WHERE Id NOT IN (" + ids + ")");
                } else {
                    st.executeUpdate("DELETE FROM acx_auction");
                }
            }

            // 3. Batch Insert Transactions
            try (PreparedStatement ps = con.prepareStatement(transSql)) {
                for (AuctionTransaction tx : TRANSACTIONS) {
                    ps.setString(1, tx.getId().toString());
                    ps.setLong(2, tx.getTransactedAt().toEpochMilli());
                    ps.setString(3, tx.getBuyerId().toString());
                    ps.setString(4, tx.getSellerId().toString());
                    ps.setLong(5, tx.getPrice());
                    ps.setBytes(6, tx.getItem().serializeAsBytes());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            con.commit();
            Log.info("[AuctionManager] saveAll() completed in {}ms", System.currentTimeMillis() - t);
        } catch (Exception e) {
            Log.error("[AuctionManager] saveAll() failed");
            e.printStackTrace();
        }
    }
    // ── Listing queries ──────────────────────────────────────────────────────

    public static List<AuctionListing> getListings() {
        return List.copyOf(LISTINGS.values());
    }

    @Nullable
    public static AuctionListing getListing(@NotNull UUID id) {
        return LISTINGS.get(id);
    }

    public static boolean hasListing(@NotNull UUID id) {
        return LISTINGS.containsKey(id);
    }

    public static boolean ownsListing(@NotNull UUID playerId, @NotNull UUID listingId) {
        AuctionListing l = LISTINGS.get(listingId);
        return l != null && l.getOwnerId().equals(playerId);
    }

    public static boolean removeListing(@NotNull UUID listingId) {
        return LISTINGS.remove(listingId) != null;
    }

    // ── Transaction queries ──────────────────────────────────────────────────

    public static List<AuctionTransaction> getTransactions() {
        return Collections.unmodifiableList(TRANSACTIONS);
    }

    public static List<AuctionTransaction> getPlayerTransactions(@NotNull UUID playerId) {
        return TRANSACTIONS.stream()
                .filter(t -> t.getBuyerId().equals(playerId) || t.getSellerId().equals(playerId))
                .toList();
    }

    // ── Offline sales ────────────────────────────────────────────────────────

    public static void logOfflineSale(@NotNull AuctionTransaction tx) {
        OFFLINE_SALES.computeIfAbsent(tx.getSellerId(), k -> new CopyOnWriteArrayList<>()).add(tx);
    }

    public static boolean hasOfflineSale(@NotNull UUID playerId) {
        return OFFLINE_SALES.containsKey(playerId);
    }

    public static int countSalesWhileOffline(@NotNull UUID playerId) {
        return OFFLINE_SALES.getOrDefault(playerId, Collections.emptyList()).size();
    }

    public static long getTotalEarningsWhileOffline(@NotNull UUID playerId) {
        return OFFLINE_SALES.getOrDefault(playerId, Collections.emptyList())
                .stream()
                .mapToLong(AuctionTransaction::getPrice)
                .sum();
    }

    public static boolean hasSingleOfflineSale(@NotNull UUID playerId) {
        return countSalesWhileOffline(playerId) == 1;
    }


    @Nullable
    public static AuctionTransaction getSingleOfflineSale(@NotNull UUID playerId) {
        List<AuctionTransaction> txs = OFFLINE_SALES.get(playerId);
        if (txs != null && txs.size() == 1) {
            return txs.getFirst();
        }
        return null;
    }

    public static void consumeOfflineSales(@NotNull UUID sellerId) {
        OFFLINE_SALES.remove(sellerId);
    }

    public static void onLogin(Player p) {
        if (AuctionManager.hasOfflineSale(p.getUniqueId())) {
            FoliaScheduler.getEntityScheduler().runDelayed(p, Arcane.getPlugin(), task -> {
                UUID playerId = p.getUniqueId();
                TextComponent message = null;

                if (AuctionManager.hasSingleOfflineSale(playerId)) {
                    AuctionTransaction tx = AuctionManager.getSingleOfflineSale(playerId);
                    if (tx != null) {

                        String itemName = PlainTextComponentSerializer.plainText().serialize(tx.getItem().effectiveName());

                        message = Component.text("Your ", NamedTextColor.GRAY)
                                .append(Component.text(itemName, Colors.HOT_PINK))
                                .append(Component.text(" was bought for ", NamedTextColor.GRAY))
                                .append(Component.text("$" + Text.formatShortBalance(tx.getPrice()),Colors.HOT_PINK))
                                .append(Component.text(" while you were away!", NamedTextColor.GRAY));
                    }
                } else {
                    int soldCount = AuctionManager.countSalesWhileOffline(playerId);
                    if (soldCount > 1) {
                        long totalSold = AuctionManager.getTotalEarningsWhileOffline(playerId);

                        message = Component.text("You earned ", NamedTextColor.GRAY)
                                .append(Component.text("$" + Text.formatShortBalance(totalSold), Colors.HOT_PINK))
                                .append(Component.text(" from auction while you were away!", NamedTextColor.GRAY));
                    }
                }

                if (message != null) {
                    p.sendMessage(message);
                    p.sendActionBar(message);
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 1.0f);
                }

                AuctionManager.consumeOfflineSales(playerId);

            }, null, 40L);
        }
    }

    // ── Item return ──────────────────────────────────────────────────────────

    public static void returnItem(@NotNull Player p, @NotNull ItemStack item) {
        if (item.getType().isAir()) return;
        Map<Integer, ItemStack> leftovers = p.getInventory().addItem(item.clone());
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(stack -> p.getWorld().dropItemNaturally(p.getLocation(), stack));
            FoliaScheduler.getEntityScheduler().execute(p, Arcane.getPlugin(), () ->
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f), null, 0L);
        }
    }

    // ── Validation ───────────────────────────────────────────────────────────

    public static boolean canSell(@NotNull Player p, @NotNull ItemStack item, long price) {
        if (item.getType().isAir()) {
            sendError(p, "You must hold an item to sell!");
            return false;
        }

        if (price < 1) {
            sendError(p, "Price must be at least $1!");
            return false;
        }

        if (price > 1_000_000_000_000L) {
            sendError(p, "That price is too high!");
            return false;
        }

        long minPrice = PriceManager.getSellPrice(item);
        if (price < minPrice) {
            sendError(p, "You can't sell below the item's worth of $" + Text.formatShortBalance(minPrice) + "!");
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (ItemUtils.hasPDC(pdc, "cit_type") && ItemUtils.hasPDC(pdc, "cit_generator_material_type")) {
                sendError(p, "You can't sell that item!");
                return false;
            }
        }

        return true;
    }

    // ── Core sell ────────────────────────────────────────────────────────────

    public static void executeSell(@NotNull Player p, @NotNull ItemStack item, long price, boolean removeFromHand) {
        AuctionListing listing = new AuctionListing(
                UUID.randomUUID(), p.getUniqueId(), Instant.now(), item, price);
        LISTINGS.put(listing.getId(), listing);

        if (removeFromHand) p.getInventory().removeItem(item);

        p.playSound(p.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1f, 1f);

        Component broadcast = Component.text(p.getName(), Colors.HOT_PINK)
                .append(Component.text(" listed ", NamedTextColor.GRAY))
                .append(Component.text(item.getAmount() + " ", Colors.HOT_PINK))
                .append(item.effectiveName().color(Colors.HOT_PINK))
                .append(Component.text(" in the auction for ", NamedTextColor.GRAY))
                .append(Component.text("$" + Text.formatShortBalance(price), Colors.HOT_PINK));

        PlayerManager.broadcast(broadcast, Sound.BLOCK_NOTE_BLOCK_HAT);
    }

    // ── Core buy ─────────────────────────────────────────────────────────────

    public enum BuyResult {
        SUCCESS, NOT_FOUND, OWN_LISTING, NO_MONEY, NO_SPACE
    }

    public static BuyResult executeBuy(@NotNull Player buyer, @NotNull UUID listingId) {
        AuctionListing listing = LISTINGS.get(listingId);
        if (listing == null) return BuyResult.NOT_FOUND;

        if (listing.getOwnerId().equals(buyer.getUniqueId())) return BuyResult.OWN_LISTING;

        if (!canFitItem(buyer, listing.getItem())) return BuyResult.NO_SPACE;

        if (EcoManager.getMoney(buyer.getUniqueId()) < listing.getPrice()) return BuyResult.NO_MONEY;

        // Atomic removal — prevents double-buy race
        if (!LISTINGS.remove(listingId, listing)) return BuyResult.NOT_FOUND;

        EcoManager.transferMoney(buyer.getUniqueId(), listing.getOwnerId(), listing.getPrice());

        PlayerManager.getMeta(buyer.getUniqueId()).setTotalAuctionSpent(PlayerManager.getMeta(buyer.getUniqueId()).getTotalAuctionSpent() + listing.getPrice());
        PlayerManager.getMeta(listing.getOwnerId()).setTotalAuctionEarned(PlayerManager.getMeta(listing.getOwnerId()).getTotalAuctionEarned() + listing.getPrice());

        AuctionTransaction tx = new AuctionTransaction(
                Instant.now(), listing.getItem(), listing.getPrice(),
                buyer.getUniqueId(), listing.getOwnerId());
        TRANSACTIONS.add(tx);

        returnItem(buyer, listing.getItem());

        Player seller = PlayerManager.getPlayer(listing.getOwnerId());
        if (seller != null) {
            FoliaScheduler.getEntityScheduler().execute(seller, Arcane.getPlugin(), () -> {
                TextComponent msg = Component.text(buyer.getName(), Colors.HOT_PINK)
                        .append(Component.text(" bought your ", NamedTextColor.GRAY))
                        .append(listing.getItem().effectiveName().color(Colors.HOT_PINK))
                        .append(Component.text(" for ", NamedTextColor.GRAY))
                        .append(Component.text("$" + Text.formatShortBalance(listing.getPrice()), Colors.HOT_PINK));
                seller.sendMessage(msg);
                seller.sendActionBar(msg);
                seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }, null, 0L);
        } else {
            logOfflineSale(tx);
        }

        return BuyResult.SUCCESS;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    private static boolean canFitItem(@NotNull Player p, @NotNull ItemStack item) {
        int space = 0;
        for (ItemStack slot : p.getInventory().getStorageContents()) {
            if (slot == null) space += item.getMaxStackSize();
            else if (slot.isSimilar(item)) space += item.getMaxStackSize() - slot.getAmount();
        }
        return space >= item.getAmount();
    }
}