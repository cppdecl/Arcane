package cx.arcane.managers.skinManager;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cx.arcane.Arcane;
import cx.arcane.managers.dbManager.DBManager;
import cx.arcane.managers.playerManager.*;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import lombok.Data;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SkinManager {

    public record Skin(String data, String signature) {}

    private static final String MOJANG_BASE_URL = "https://eclipse.skinsrestorer.net/mojang";
    private static final String GEYSER_BASE_URL = "https://api.geysermc.org/v2/skin/";
    private static final ConcurrentHashMap<UUID, CachedSkin> cachedSkins = new ConcurrentHashMap<>();

    @Getter
    private static Skin defaultSkin;

    public static void onEnable() {
        Log.info("[Skins] Enabling SkinManager...");
        if (createTable()) {
            Log.info("[Skins] Database table verified.");
        }
        loadAll();
        Log.info("[Skins] Found {} cached skins.", cachedSkins.size());

        defaultSkin = getSkinByName("ZaBIe");
        Log.info("[Skins] Default skin loaded: {}", (defaultSkin != null) ? "Success" : "Failed");

        Log.info("[Skins] SkinManager enabled.");
    }

    public static void onDisable() {
        Log.info("[Skins] Disabling SkinManager, saving {} entries...", cachedSkins.size());
        saveAll();
        Log.info("[Skins] SkinManager disabled.");
    }

    public static void onSave() {
        createTable();
        saveAll();
    }

    private static boolean createTable() {
        String sql = """
        CREATE TABLE IF NOT EXISTS acx_skins (
            UniqueId VARCHAR(36) NOT NULL,
            CachedSkinData JSON NULL,
            PRIMARY KEY (UniqueId),
            INDEX idx_acx_skins_id (UniqueId)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
        """;
        try (Connection con = DBManager.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
            return true;
        } catch (SQLException e) {
            Log.error("[Skins] CRITICAL: Failed to create table: {}", e.getMessage());
            return false;
        }
    }

    private static boolean loadAll() {
        Log.info("[Skins] Fetching skins from database...");
        try (Connection con = DBManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM acx_skins");
             ResultSet rs = ps.executeQuery()) {
            int count = 0;
            while (rs.next()) {
                try {
                    String skinJson = rs.getString("CachedSkinData");
                    if (skinJson != null && !skinJson.isBlank()) {
                        CachedSkin data = new ObjectMapper().readValue(skinJson, CachedSkin.class);
                        cachedSkins.put(data.getUniqueId(), data);
                        count++;
                    }
                } catch (Exception e) {
                    Log.error("[Skins] Failed to deserialize skin for UUID: {}", rs.getString("UniqueId"));
                }
            }
            Log.info("[Skins] Successfully synchronized {} skins.", count);
            return true;
        } catch (Exception e) {
            Log.error("[Skins] Failed to load skins: {}", e.getMessage());
            return false;
        }
    }

    public static boolean saveAll() {
        String sql = "INSERT INTO acx_skins (UniqueId, CachedSkinData) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE CachedSkinData = VALUES(CachedSkinData)";
        try (Connection con = DBManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            con.setAutoCommit(false);
            for (CachedSkin data : cachedSkins.values()) {
                ps.setString(1, data.getUniqueId().toString());
                ps.setString(2, new ObjectMapper().writeValueAsString(data));
                ps.addBatch();
            }
            ps.executeBatch();
            con.commit();
            Log.info("[Skins] Bulk save complete.");
            return true;
        } catch (Exception e) {
            Log.error("[Skins] Failed to save skins: {}", e.getMessage());
            return false;
        }
    }

    // --- COMMAND HANDLERS (For Chat/Console commands) ---

    public static void setSkinCommand(CommandSender sender, Player target, String skinName) {
        Log.info("[Skins] {} requested skin '{}' for {}", sender.getName(), skinName, target.getName());

        Arcane.getPlugin().getServer().getAsyncScheduler().runNow(Arcane.getPlugin(), task -> {
            Skin skin = getSkinByName(skinName);

            target.getScheduler().run(Arcane.getPlugin(), t -> {
                if (skin == null) {
                    Log.warn("[Skins] Fetch failed for name: {}", skinName);
                    notifyError(sender, "Skin " + skinName + " does not exist.");
                    return;
                }

                CachedSkin cSkin = cachedSkins.computeIfAbsent(target.getUniqueId(), k -> new CachedSkin(target.getUniqueId(), target.getName()));
                cSkin.setSkinData(skin.data());
                cSkin.setSkinSignature(skin.signature());

                applySkin(target, skin);
                Log.info("[Skins] Successfully applied '{}' to {}", skinName, target.getName());

                notifySuccess(sender, "Skin has been updated.");
            }, null);
        });
    }

    public static void clearSkinCommand(CommandSender sender, Player target) {
        Log.info("[Skins] {} is clearing custom skin for {}", sender.getName(), target.getName());

        Arcane.getPlugin().getServer().getAsyncScheduler().runNow(Arcane.getPlugin(), task -> {
            Skin skin = getSkinByName(target.getName());

            target.getScheduler().run(Arcane.getPlugin(), t -> {
                if (skin == null) {
                    Log.error("[Skins] Clear failed for {}", target.getName());
                    notifyError(sender, "Failed to update skin.");
                    return;
                }

                CachedSkin cSkin = cachedSkins.get(target.getUniqueId());
                if (cSkin != null) {
                    cSkin.setSkinData(skin.data());
                    cSkin.setSkinSignature(skin.signature());
                }

                applySkin(target, skin);
                Log.info("[Skins] Reset {} to default.", target.getName());
                notifySuccess(sender, "Skin has been set to default.");
            }, null);
        });
    }

    // --- PROGRAMMATIC API (For Menus, Quests, etc.) ---

    public static void setSkin(Player p, String skinName) {
        CachedSkin cSkin = cachedSkins.get(p.getUniqueId());
        if (cSkin == null) return;

        Arcane.getPlugin().getServer().getAsyncScheduler().runNow(Arcane.getPlugin(), task -> {
            Skin skin = getSkinByName(skinName);
            if (skin == null) return;

            cSkin.setSkinData(skin.data());
            cSkin.setSkinSignature(skin.signature());

            p.getScheduler().run(Arcane.getPlugin(), t -> applySkin(p, skin), null);
        });
    }

    public static void clearSkin(Player p) {
        CachedSkin cSkin = cachedSkins.get(p.getUniqueId());
        if (cSkin == null) return;

        Arcane.getPlugin().getServer().getAsyncScheduler().runNow(Arcane.getPlugin(), task -> {
            Skin skin = getSkinByName(p.getName());
            if (skin == null) return;

            cSkin.setSkinData(skin.data());
            cSkin.setSkinSignature(skin.signature());

            p.getScheduler().run(Arcane.getPlugin(), t -> applySkin(p, skin), null);
        });
    }

    public static CachedSkin getCachedSkin(UUID uuid) {
        return cachedSkins.get(uuid);
    }

    public static Skin getPlayerSkin(String username, UUID uniqueId) {
        CachedSkin cSkin = cachedSkins.get(uniqueId);

        if (cSkin == null) {
            Skin skinOfName = getSkinByName(username);

            if (skinOfName == null) {
                return getDefaultSkin();
            }

            return skinOfName;
        }

        return new Skin(cSkin.getSkinData(), cSkin.getSkinSignature());
    }

    // --- LOGIN & PROFILE HANDLING ---

    public static PlayerProfile handlePreLogin(AsyncPlayerPreLoginEvent e, PlayerProfile profile) {
        Log.info("[Skins] Handling pre-login for {} ({}).", profile.getName(), profile.getId());
        PlayerSession pSession = PlayerManager.getSession(profile.getId());
        Skin skin = getPlayerSkin(profile.getName(), profile.getId());
        return applySkin(profile, skin.data(), skin.signature());
    }

    public static void handleLogin(Player p) {
        p.getScheduler().runDelayed(Arcane.getPlugin(), t -> {
            applySkin(p, getPlayerSkin(p.getName(), p.getUniqueId()));
        }, null, 1);

        if (cachedSkins.containsKey(p.getUniqueId())) {
            return;
        }

        ProfileProperty textures = p.getPlayerProfile().getProperties()
                .stream()
                .filter(prop -> prop.getName().equals("textures"))
                .findFirst()
                .orElse(null);

        if (textures == null) {
            return;
        }

        CachedSkin newSkin = new CachedSkin();
        newSkin.setUniqueId(p.getUniqueId());
        newSkin.setSkinData(textures.getValue());
        newSkin.setSkinSignature(textures.getSignature());
        cachedSkins.put(p.getUniqueId(), newSkin);
    }

    // --- APPLY SKIN UTILS ---

    private static void applySkin(Player player, Skin skin) {
        if (skin == null) return;
        PlayerProfile profile = player.getPlayerProfile();
        profile.removeProperty("textures");
        profile.setProperty(new ProfileProperty("textures", skin.data(), skin.signature()));
        player.setPlayerProfile(profile);
    }

    private static PlayerProfile applySkin(PlayerProfile profile, Skin skin) {
        if (skin == null || profile == null) return null;
        profile.removeProperty("textures");
        profile.setProperty(new ProfileProperty("textures", skin.data(), skin.signature()));
        return profile;
    }

    private static PlayerProfile applySkin(PlayerProfile profile, String skinData, String skinSignature) {
        if (skinData == null || skinSignature == null || profile == null) return null;
        profile.removeProperty("textures");
        profile.setProperty(new ProfileProperty("textures", skinData, skinSignature));
        return profile;
    }

    // --- NETWORK FETCHING UTILS ---

    private static Skin getSkinByName(String name) {
        if (name.equals("Steve")) {
            return getDefaultSkin();
        }

        if (name.startsWith(".")) {
            Log.info("[Skins] Name '{}' contains '.', fetching Bedrock skin via Geyser.", name);
            Long xuid = getXuidFromName(name);
            return (xuid != null) ? getBedrockSkinByXUID(xuid) : null;
        }

        // Standard Java logic
        try {
            URL uuidUrl = new URL(MOJANG_BASE_URL + "/uuid/" + name);
            HttpURLConnection conn = (HttpURLConnection) uuidUrl.openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) return null;

            JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
            if (!json.get("exists").getAsBoolean()) return null;

            return getSkinByUniqueId(UUID.fromString(json.get("uuid").getAsString()));
        } catch (Exception e) {
            Log.error("[Skins] Java fetch error for {}: {}", name, e.getMessage());
            return null;
        }
    }

    public static Long getXuidFromName(String gamertag) {
        String cleanName = gamertag.startsWith(".") ? gamertag.substring(1) : gamertag;
        try {
            return FloodgateApi.getInstance().getXuidFor(cleanName).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.error("[Skins] Failed to resolve XUID for {}: {}", gamertag, e.getMessage());
            return null;
        }
    }

    private static Skin getBedrockSkinByXUID(long xuid) {
        try {
            URL url = new URL(GEYSER_BASE_URL + xuid);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) return null;

            JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();

            if (!json.has("value") || !json.has("signature")) {
                Log.error("[Skins] Geyser API returned no skin data for XUID {}", xuid);
                return null;
            }

            return new Skin(json.get("value").getAsString(), json.get("signature").getAsString());
        } catch (Exception e) {
            Log.error("[Skins] Geyser fetch error for {}: {}", xuid, e.getMessage());
            return null;
        }
    }

    private static Skin getSkinByUniqueId(UUID uniqueId) {
        try {
            URL skinUrl = new URL(MOJANG_BASE_URL + "/skin/" + uniqueId);
            HttpURLConnection conn = (HttpURLConnection) skinUrl.openConnection();
            if (conn.getResponseCode() != 200) return null;

            JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
            if (!json.get("exists").getAsBoolean()) return null;

            JsonObject prop = json.getAsJsonObject("skinProperty");
            return new Skin(prop.get("value").getAsString(), prop.get("signature").getAsString());
        } catch (Exception e) {
            return null;
        }
    }

    private static void notifySuccess(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, Colors.HOT_PINK));
        if (sender instanceof Player p) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.MASTER, 1, 1);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1, 1);
        }
    }

    private static void notifyError(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, Colors.DARK_PINK));
        if (sender instanceof Player p) p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }
}