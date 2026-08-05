package cx.arcane.managers.interactionManager.crystals;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import cx.arcane.Arcane;
import cx.arcane.managers.playerManager.PlayerManager;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class CrystalEventListener implements Listener {

    @EventHandler
    public void onAnchorInteract(PlayerInteractEvent event) {
        // Only main hand
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        if (block.getType() != Material.RESPAWN_ANCHOR) return;
        if (block.getWorld().getEnvironment() == World.Environment.NETHER) return;

        RespawnAnchor anchorData = (RespawnAnchor) block.getBlockData();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.GLOWSTONE && anchorData.getCharges() > 0) {
            incrementAnchorCounter(player);
            return;
        }

        if (item.getType() == Material.GLOWSTONE && anchorData.getCharges() >= 4) {
            incrementAnchorCounter(player);
        }
    }

    private void incrementAnchorCounter(Player player) {
        long current = PlayerManager.getByUniqueId(player.getUniqueId())
                .getMeta().getAnchorsExploded();
        PlayerManager.getByUniqueId(player.getUniqueId())
                .getMeta().setAnchorsExploded(current + 1);
    }

    @EventHandler
    public void onCrystalInteract(EntityDamageByEntityEvent e) {
        if (e.getEntityType() == EntityType.END_CRYSTAL) {
            if (e.getDamager() instanceof Player player) {
                long current = PlayerManager.getByUniqueId(player.getUniqueId())
                        .getMeta().getCrystalsExploded();
                PlayerManager.getByUniqueId(player.getUniqueId())
                        .getMeta().setCrystalsExploded(current + 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (e.getEntityType() != EntityType.END_CRYSTAL) return;
        CrystalTracker.addCrystal((EnderCrystal) e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent e) {
        if (e.getEntityType() != EntityType.END_CRYSTAL) return;
        String world = e.getEntity().getWorld().getName();
        int id = e.getEntity().getEntityId();
        FoliaScheduler.getEntityScheduler().runDelayed(
                e.getEntity(), Arcane.getPlugin(),
                t -> CrystalTracker.removeCrystal(world, id),
                null, CrystalTracker.REMOVE_DELAY
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent e) {
        CrystalTracker.unloadWorld(e.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        CrystalTracker.newUser(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent e) {
        CrystalTracker.removeUser(e.getPlayer().getUniqueId());
    }
}