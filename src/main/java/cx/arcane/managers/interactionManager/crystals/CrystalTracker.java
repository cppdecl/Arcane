package cx.arcane.managers.interactionManager.crystals;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import cx.arcane.Arcane;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CrystalTracker {

    private static final ConcurrentHashMap<String, ConcurrentHashMap<Integer, EnderCrystal>> CRYSTALS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, CrystalUser> USERS = new ConcurrentHashMap<>();

    static final Set<Material> AIR_TYPES  = Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);
    static final Set<Material> BLOCK_TYPES = Set.of(Material.OBSIDIAN, Material.BEDROCK);

    static final long REMOVE_DELAY = 10L;

    public static void onEnable() {
        Bukkit.getPluginManager().registerEvents(new CrystalEventListener(), Arcane.getPlugin());
        PacketEvents.getAPI().getEventManager().registerListener(
                new CrystalPacketListener(), PacketListenerPriority.LOWEST);

        for (Player p : Bukkit.getOnlinePlayers()) newUser(p);
    }

    public static void onDisable() {
        CRYSTALS.clear();
        USERS.clear();
    }

    public static void newUser(Player p) {
        USERS.put(p.getUniqueId(), new CrystalUser(p, AnimationType.MISC, false));
    }

    public static CrystalUser getUser(UUID id) {
        return USERS.get(id);
    }

    public static void removeUser(UUID id) {
        USERS.remove(id);
    }

    public static void addCrystal(EnderCrystal crystal) {
        CRYSTALS.computeIfAbsent(crystal.getWorld().getName(), k -> new ConcurrentHashMap<>())
                .put(crystal.getEntityId(), crystal);
    }

    public static EnderCrystal getCrystal(String world, int entityId) {
        ConcurrentHashMap<Integer, EnderCrystal> map = CRYSTALS.get(world);
        return map != null ? map.get(entityId) : null;
    }

    public static void removeCrystal(String world, int entityId) {
        ConcurrentHashMap<Integer, EnderCrystal> map = CRYSTALS.get(world);
        if (map != null) map.remove(entityId);
    }

    public static Iterable<EnderCrystal> getCrystals(String world) {
        ConcurrentHashMap<Integer, EnderCrystal> map = CRYSTALS.get(world);
        return map != null ? map.values() : java.util.Collections.emptyList();
    }

    public static void unloadWorld(String world) {
        CRYSTALS.remove(world);
    }

    public static void spawnCrystal(Location loc, Player player, ItemStack item) {
        Location clonedLoc = loc.clone().subtract(0.5, 0.0, 0.5);
        if (!AIR_TYPES.contains(clonedLoc.getBlock().getType())) return;

        clonedLoc.add(0.5, 1.0, 0.5);
        List<Entity> nearbyEntities = new ArrayList<>(clonedLoc.getWorld().getNearbyEntities(clonedLoc, 0.5, 1, 0.5,
                entity -> !(entity instanceof Player p) || p.getGameMode() != GameMode.SPECTATOR));

        if (nearbyEntities.isEmpty()) {
            loc.getWorld().spawn(clonedLoc.subtract(0.0, 1.0, 0.0), EnderCrystal.class, entity -> entity.setShowingBottom(false));

            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                item.setAmount(item.getAmount() - 1);
            }
        }
    }
}