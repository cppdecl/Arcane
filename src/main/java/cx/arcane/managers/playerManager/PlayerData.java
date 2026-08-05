package cx.arcane.managers.playerManager;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cx.arcane.Arcane;
import cx.arcane.managers.authManager.AuthManager;
import cx.arcane.managers.authManager.AuthState;
import cx.arcane.managers.clanManager.ClanManager;
import cx.arcane.managers.clanManager.clanInfo.ClanData;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.geoManager.GeoData;
import cx.arcane.utils.NetUtils;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerData {

    private UUID uniqueId;
    private String name;
    private String password;
    private Instant registerAt;
    private InetAddress registerAddress;
    private Instant lastLoginAt;
    private InetAddress lastLoginAddress;
    private List<InetAddress> addressHistory = new ArrayList<>();
    private String discordId;
    private GeoData lastGeoData;
    private String timezone;

    private String channel = "Default";

    private PlayerSettings settings = null;
    private PlayerMeta meta = null;

    public ClanData getClan() {
        return ClanManager.getPlayerClan(uniqueId);
    }

    public boolean hasClan() {
        return ClanManager.hasClan(uniqueId);
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public PlayerSession getSession() {
        if (isOnline()) {
            return NetUtils.getSession(getPlayer().getAddress());
        }
        return null;
    }

    public boolean isOnline() {
        Player p = Bukkit.getPlayer(this.uniqueId);
        if (p == null) return false;

        AuthState aState = AuthManager.getAuthSession(p.getUniqueId());
        if (aState == null) return false;

        return aState.isAuthenticated();
    }

    public Player getPlayer() {
        Player p = Bukkit.getPlayer(this.uniqueId);
        if (p == null) return null;

        AuthState aState = AuthManager.getAuthSession(p.getUniqueId());
        if (aState == null || !aState.isAuthenticated()) return null;

        return p;
    }

    public EntityScheduler getScheduler() {
        Player p = getPlayer();
        if (p == null) return null;

        return p.getScheduler();
    }

    public void run(@NotNull Runnable task) {
        run(task, null);
    }

    public void run(@NotNull Runnable task, @Nullable Runnable retired) {
        var scheduler = getScheduler();
        if (scheduler == null) {
            if (retired != null) retired.run();
            return;
        }

        scheduler.run(Arcane.getPlugin(), t -> {
            task.run();
        }, retired);
    }

    public boolean isOffline() {
        return !isOnline();
    }

    public String getTimezone() {
        if (timezone == null) {
            return "UTC";
        }

        return timezone;
    }

    public void setTimezone(String timezone) {
        if (timezone == null) {
            this.timezone = "UTC";
            return;
        }

        this.timezone = timezone;
    }

    private String getSafeLocationInfo() {
        if (lastGeoData == null) return "Unknown Location";
        StringBuilder location = new StringBuilder();
        if (lastGeoData.getCity() != null && !lastGeoData.getCity().isEmpty()) {
            location.append(lastGeoData.getCity());
        }
        if (lastGeoData.getRegion() != null && !lastGeoData.getRegion().isEmpty()) {
            if (!location.isEmpty()) location.append(", ");
            location.append(lastGeoData.getRegion());
        }
        if (lastGeoData.getCountry() != null && !lastGeoData.getCountry().isEmpty()) {
            if (!location.isEmpty()) location.append(", ");
            location.append(lastGeoData.getCountry());
        }
        if (location.isEmpty()) {
            return "Unknown Location";
        }
        return location.toString();
    }

    private String getSafeIspInfo() {
        if (lastGeoData == null) return "Unknown ISP";
        String isp = lastGeoData.getIsp();
        Long asn = lastGeoData.getAsn();
        if (isp == null || isp.isEmpty()) {
            return "Unknown ISP";
        }

        StringBuilder ispInfo = new StringBuilder();
        if (!isp.isEmpty()) {
            ispInfo.append(isp);
        }

        if (asn != null) {
            if (!ispInfo.isEmpty()) {
                ispInfo.append(" ");
            }
            ispInfo.append("(AS").append(asn).append(")");
        }

        return ispInfo.toString();
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getUsername() {
        return name;
    }

    public void setUsername(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isPremium() {
        return password.equals(" -- Premium Account -- ");
    }

    public boolean isBedrock() { return password.equals(" -- Bedrock Account -- "); }

    public boolean isCracked() { return !isPremium() && !isBedrock(); }

    public AccountType getAccountType() {
        if (isPremium()) return AccountType.PREMIUM;
        if (isBedrock()) return AccountType.BEDROCK;

        return AccountType.CRACKED;
    }

    public String getAccountTypeString() {
        if (isPremium()) return "Premium";
        if (isBedrock()) return "Bedrock";

        return "Cracked";
    }

    public Instant getRegisterAt() {
        return registerAt;
    }

    public void setRegisterAt(Instant registerAt) {
        this.registerAt = registerAt;
    }

    public InetAddress getRegisterAddress() {
        return registerAddress;
    }

    public void setRegisterAddress(InetAddress registerAddress) {
        this.registerAddress = registerAddress;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public InetAddress getLastLoginAddress() {
        return lastLoginAddress;
    }

    public void setLastLoginAddress(InetAddress lastLoginAddress) {
        this.lastLoginAddress = lastLoginAddress;
    }

    public PlayerSettings getSettings() {
        if (settings == null)
            settings = new PlayerSettings();

        return settings;
    }

    public void setSettings(PlayerSettings settings) {
        this.settings = settings;
    }

    public PlayerMeta getMeta() {
        if (meta == null) meta = new PlayerMeta();
        return meta;
    }

    public void setMeta(PlayerMeta meta) {
        this.meta = meta;
    }

    public List<InetAddress> getAddressHistory() {
        return addressHistory;
    }

    public void setAddressHistory(ArrayList<InetAddress> addressHistory) {
        this.addressHistory = addressHistory;
    }

    public void addToAddressHistory(InetAddress address) {
       if (!this.addressHistory.contains(address)) {
           addressHistory.add(address);
       }
    }

    public String getDiscordId() {
        return discordId;
    }

    public void setDiscordId(String discordId) {
        this.discordId = discordId;
    }

    public boolean isDiscordLinked() {
        return this.discordId != null && !discordId.isEmpty();
    }

    public GeoData getLastGeoData() {
        return this.lastGeoData;
    }

    public void setLastGeoData(GeoData data) {
        this.lastGeoData = data;
    }

    /*
     * Economy wrapper methods
     *
     * THREAD SAFETY:
     * Safe from ANY thread because EcoManager is synchronized.
     */

    public boolean takeMoney(long amount) {
        return EcoManager.takeMoney(uniqueId, amount);
    }

    public void giveMoney(long amount) {
        EcoManager.giveMoney(uniqueId, amount);
    }

    public void setMoney(long amount) {
        EcoManager.setMoney(uniqueId, amount);
    }

    public long getMoney() {
        return EcoManager.getMoney(uniqueId);
    }

    public boolean transferMoney(UUID to, long amount) {
        return EcoManager.transferMoney(uniqueId, to, amount);
    }

    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    @JsonIgnore
    public Location getLocation() {
        if (isOnline()) {
            return getPlayer().getLocation();
        }

        if (world == null) {
            return null;
        }

        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    @JsonIgnore
    public void setLocation(Location location) {

        if (isOnline()) {
            getPlayer().teleportAsync(location);
            return;
        }

        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }
}
