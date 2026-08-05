package cx.arcane.managers.priceManager;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import cx.arcane.Arcane;
import cx.arcane.managers.commandManager.commands.ShopCommand;
import cx.arcane.managers.dbManager.DBManager;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.itemManager.ItemManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import cx.arcane.utils.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PriceManager {

    private static final ConcurrentHashMap<Material, PriceData> cache = new ConcurrentHashMap<>();

    public static void onEnable() {
        Log.info("[PriceManager] Enabling...");
        createTable();
        loadAll();
        syncRegistry();
        loadJsonPrices();
        Log.info("[PriceManager] Enabled with {} cached entries.", cache.size());
    }

    public static void onDisable() {
        Log.info("[PriceManager] Disabling, saving {} entries...", cache.size());
        saveAll();
        Log.info("[PriceManager] Disabled.");
    }

    public static void onSave() {
        createTable();
        saveAll();
    }

    private static void createTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS acx_prices (
                Material VARCHAR(64) NOT NULL,
                Name     VARCHAR(128) NOT NULL,
                Price    BIGINT UNSIGNED NOT NULL DEFAULT 0,
                PRIMARY KEY (Material)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
            """;

        try (Connection con = DBManager.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            Log.error("[PriceManager] Failed to create table: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void loadAll() {
        Log.info("[PriceManager] loadAll() started.");
        long tTotal = System.currentTimeMillis();

        try {
            long tConn = System.currentTimeMillis();
            Connection con = DBManager.getConnection();
            Log.info("[PriceManager] getConnection() took {}ms", System.currentTimeMillis() - tConn);

            try (con;
                 PreparedStatement ps = con.prepareStatement("SELECT Material, Price FROM acx_prices");
                 ResultSet rs = ps.executeQuery()) {

                int loaded = 0;
                int failed = 0;

                long tQuery = System.currentTimeMillis();
                Log.info("[PriceManager] Query executed in {}ms", System.currentTimeMillis() - tQuery);

                long tParse = System.currentTimeMillis();
                while (rs.next()) {
                    try {
                        String key = rs.getString("Material");
                        long price = rs.getLong("Price");

                        Material material = Material.matchMaterial(key);
                        if (material == null) {
                            Log.error("[PriceManager] Unknown material key in DB: {}", key);
                            failed++;
                            continue;
                        }

                        PriceData data = new PriceData(material);
                        data.setPrice(price);
                        cache.put(material, data);
                        loaded++;

                    } catch (Exception e) {
                        Log.error("[PriceManager] Failed to deserialize row: {}", e.getMessage());
                        failed++;
                    }
                }
                Log.info("[PriceManager] Row parsing took {}ms", System.currentTimeMillis() - tParse);
                Log.info("[PriceManager] loadAll() completed in {}ms — {} loaded, {} failed.",
                        System.currentTimeMillis() - tTotal, loaded, failed);

            }
        } catch (Exception e) {
            Log.error("[PriceManager] Failed to load from database: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void syncRegistry() {
        Log.info("[PriceManager] syncRegistry() started.");
        long tTotal = System.currentTimeMillis();

        List<Material> missing = Registry.MATERIAL.stream()
                .filter(m -> m.isItem() && !m.isAir() && !m.isLegacy())
                .filter(m -> !cache.containsKey(m))
                .toList();

        Log.info("[PriceManager] Registry scan took {}ms — {} missing materials.",
                System.currentTimeMillis() - tTotal, missing.size());

        if (missing.isEmpty()) return;

        for (Material m : missing)
            cache.put(m, new PriceData(m));

        Log.info("[PriceManager] Synced {} missing materials from registry. Saving...", missing.size());
        saveAll();
    }

    public static void saveAll() {
        String sql = """
            INSERT INTO acx_prices (Material, Name, Price)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE Name = VALUES(Name), Price = VALUES(Price)
            """;

        int total = cache.size();

        long tTotal = System.currentTimeMillis();

        try {
            long tConn = System.currentTimeMillis();
            Connection con = DBManager.getConnection();

            try (con; PreparedStatement ps = con.prepareStatement(sql)) {

                con.setAutoCommit(false);

                long tBuild = System.currentTimeMillis();
                for (PriceData data : cache.values()) {
                    ps.setString(1, data.getMaterial().key().toString());
                    ps.setString(2, data.getName());
                    ps.setLong(3, data.getPrice());
                    ps.addBatch();
                }

                try {
                    long tExec = System.currentTimeMillis();
                    int[] results = ps.executeBatch();

                    long tCommit = System.currentTimeMillis();
                    con.commit();

                } catch (Exception e) {
                    Log.info("[PriceManager] Batch execution failed after {}ms — rolling back.",
                            System.currentTimeMillis() - tTotal);
                    con.rollback();
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            Log.error("[PriceManager] saveAll() failed after {}ms: {}", System.currentTimeMillis() - tTotal, e.getMessage());
            e.printStackTrace();
        }
    }

    public static void loadJsonPrices() {
        File file = new File(Arcane.getPlugin().getDataFolder(), "items.json");
        if (!file.exists()) {
            Log.info("[PriceManager] No items.json found in plugin folder.");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<JsonPrice>>() {}.getType();
            List<JsonPrice> jsonPrices = new Gson().fromJson(reader, listType);

            int applied = 0;
            for (JsonPrice jp : jsonPrices) {
                Material mat = ItemManager.getById(jp.materialId);
                if (mat != null) {
                    setPrice(mat, jp.price);
                    applied++;
                }
            }
            Log.info("[PriceManager] Applied {} prices from items.json", applied);

        } catch (Exception e) {
            Log.error("[PriceManager] Failed to load items.json: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static class JsonPrice {
        String materialId;
        long price;
    }

    public static PriceData getByMaterial(Material material) {
        return cache.get(material);
    }

    public static PriceData getById(String id) {
        Material material = ItemManager.getById(id);
        return material == null ? null : cache.get(material);
    }

    public static PriceData getByName(String name) {
        Material material = ItemManager.getByName(name);
        return material == null ? null : cache.get(material);
    }

    public static PriceData getByNameClosest(String query) {
        Material material = ItemManager.getByNameClosest(query);
        return material == null ? null : cache.get(material);
    }

    public static List<PriceData> searchByName(String query) {
        return ItemManager.searchByName(query).stream()
                .map(cache::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static Collection<PriceData> getAll() {
        return Collections.unmodifiableCollection(cache.values());
    }

    public static void setPrice(Material material, long price) {
        cache.computeIfAbsent(material, PriceData::new).setPrice(price);
    }

    private static Component formatPrice(long price) {
        return Component.text(String.format("$%,d", price), TextColor.color(0x00FC92));
    }

    public static long getSellPrice(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        Material type = item.getType();

        long outerPrice = getSellPrice(type) * item.getAmount();

        // Shulker Box
        if (type.name().endsWith("SHULKER_BOX") && item.getItemMeta() instanceof BlockStateMeta meta) {
            BlockState state = meta.getBlockState();
            if (state instanceof ShulkerBox box) {
                long contentsValue = Arrays.stream(box.getInventory().getContents())
                        .filter(Objects::nonNull)
                        .mapToLong(PriceManager::getSellPrice)
                        .sum();
                return outerPrice + contentsValue;
            }
        }

        // Bundle
        if (type.name().endsWith("BUNDLE") && item.getItemMeta() instanceof BundleMeta bundle) {
            long contentsValue = bundle.getItems().stream()
                    .filter(Objects::nonNull)
                    .mapToLong(PriceManager::getSellPrice)
                    .sum();
            return outerPrice + contentsValue;
        }

        // Normal item
        return outerPrice;
    }

    public static long getSellPrice(Material material) {
        PriceData data = getByMaterial(material);
        return data == null ? 0 : data.getPrice();
    }

    public static void checkShopPrices(CommandSender sender) {
        List<ShopCommand.ShopEntry> entries = ShopCommand.getAllShopEntries();
        boolean found = false;
        int foundCount = 0;

        for (ShopCommand.ShopEntry entry : entries) {
            long sellPrice = getSellPrice(entry.item());
            if (sellPrice <= 0) continue;
            if (entry.price() <= sellPrice) {
                if (!found) {
                    found = true;
                }
                foundCount++;
            }
        }

        if (found)
            sender.sendMessage(Component.text("Shop pricing conflict has been detected (" + foundCount + ").", Colors.HOT_PINK));

        for (ShopCommand.ShopEntry entry : entries) {
            checkShopPrice(sender, entry.item().getType(), false);
        }
    }

    public static void checkShopPrices(Player p) {
        if (!p.hasPermission("arcane.rank.management")) return;
        checkShopPrices((CommandSender) p);
    }

    public static boolean checkShopPrice(CommandSender sender, Material material, boolean header) {
        long sellPrice = getSellPrice(material);
        if (sellPrice <= 0) return true;

        ShopCommand.ShopEntry entry = ShopCommand.getAllShopEntries().stream()
                .filter(e -> e.item().getType() == material)
                .findFirst()
                .orElse(null);

        if (entry == null) return true;

        if (entry.price() <= sellPrice) {
            if (header)
                sender.sendMessage(Component.text("Shop pricing conflict has been detected.", Colors.HOT_PINK));

            sender.sendMessage(Component.text().append(
                    Component.text(" • ", Colors.HOT_PINK),
                    Component.text(ItemManager.getDisplayName(material), Colors.HOT_PINK),
                    Component.text(" priced ", Colors.GRAY),
                    Component.text("$" + entry.price(), Colors.RED),
                    Component.text(" on shop can be sold for ", Colors.GRAY),
                    Component.text("$" + sellPrice, Colors.HOT_PINK)
            ));
            return false;
        }

        return true;
    }

    public static boolean checkShopPrice(CommandSender sender, ItemStack item) {
        return checkShopPrice(sender, item.getType(), true);
    }

    public static void checkDuplicateShopPrices(CommandSender sender) {
        List<ShopCommand.ShopEntry> entries = ShopCommand.getAllShopEntries();

        // Material -> Set of prices found
        Map<Material, Set<Long>> priceMap = new HashMap<>();

        for (ShopCommand.ShopEntry entry : entries) {
            Material mat = entry.item().getType();
            if (mat == Material.SPAWNER) continue;

            priceMap.computeIfAbsent(mat, k -> new HashSet<>())
                    .add(entry.price());
        }

        boolean found = false;

        for (Map.Entry<Material, Set<Long>> e : priceMap.entrySet()) {
            if (e.getValue().size() > 1) { // multiple different prices
                if (!found) {
                    sender.sendMessage(Component.text(
                            "Duplicate shop price conflicts detected:",
                            Colors.HOT_PINK
                    ));
                    found = true;
                }

                sender.sendMessage(Component.text().append(
                        Component.text(" • ", Colors.HOT_PINK),
                        Component.text(ItemManager.getDisplayName(e.getKey()), Colors.HOT_PINK),
                        Component.text(" has multiple prices: ", NamedTextColor.GRAY),
                        Component.text(
                                e.getValue().stream()
                                        .sorted()
                                        .map(p -> "$" + p)
                                        .collect(Collectors.joining(", ")),
                                NamedTextColor.RED
                        )
                ));
            }
        }
    }

    public static TextComponent getSellWorthTextComponent(ItemStack item) {
        double sellPrice = getSellPrice(item);
        if (sellPrice <= 0.0) {
            return Component.text("Worth: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("Unsellable", TextColor.color(0xff0000)));
        } else {
            String formatted;
            if (sellPrice % 1 == 0) {
                formatted = String.format("$%,d", (long) sellPrice); // no decimal part
            } else {
                formatted = String.format("$%,.2f", sellPrice); // with 2 decimal places
            }
            return Component.text("Worth: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(formatted, TextColor.color(0x00FC92)));
        }
    }

    public static void sellBlockContents(Block block, Player player) {
        if (player.getWorld().getName().equals("spawn")) return;

        BlockState state = block.getState();
        if (!(state instanceof InventoryHolder holder)) {
            return;
        }

        var inv = holder.getInventory();
        long total = 0;
        // sum prices and remove items
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType().isAir()) continue;
            long price = getSellPrice(item);
            total += price;
        }
        // clear it out
        inv.clear();

        if (total <= 0) {
            var msg = Component.text("Nothing worth selling in that container.", NamedTextColor.GRAY);
            player.sendActionBar(msg);
            return;
        }

        // deposit to the player
        EcoManager.giveMoney(player.getUniqueId(), total);

        // play sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 2f);

        // format nicely
        String formatted = Text.formatShortBalanceWithSign("$", total);

        var msg = Component.text("Received ", NamedTextColor.GRAY)
                .append(Component.text(formatted, NamedTextColor.GREEN));
        player.sendMessage(msg);
        player.sendActionBar(msg);
    }


}