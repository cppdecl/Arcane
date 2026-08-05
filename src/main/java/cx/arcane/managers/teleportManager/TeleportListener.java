package cx.arcane.managers.teleportManager;

import cx.arcane.Arcane;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.LocationUtils;
import io.canvasmc.canvas.event.EntityTeleportAsyncEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.List;

public class TeleportListener implements Listener {

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        if (!e.isAnchorSpawn() && !e.isBedSpawn() || e.isMissingRespawnBlock()) {
            Location spawnLoc = LocationUtils.getPoint("spawn");
            if (spawnLoc == null) return;
            e.setRespawnLocation(spawnLoc);
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onTeleportAsync(EntityTeleportAsyncEvent e) {
        if (e.getEntityType() != EntityType.PLAYER) return;
        if (!(e.getEntity() instanceof Player p)) return;

        PlayerData pData = PlayerManager.getByUniqueId(p.getUniqueId());

        TeleportManager.saveLastLocation(p);

        Entity vehicle = p.getVehicle();
        if (vehicle == null) return;

        List<Entity> passengers = new ArrayList<>(vehicle.getPassengers());
        boolean isDriver = vehicle instanceof Steerable || vehicle instanceof AbstractHorse horse && horse.isTamed() && p.equals(horse.getOwner()) || vehicle instanceof Llama llama && llama.isTamed() && p.equals(llama.getOwner()) || vehicle instanceof Boat && !passengers.isEmpty() && p.equals(passengers.getFirst()) || passengers.isEmpty() || p.equals(passengers.getFirst());

        if (!isDriver) return;

        Location destination = e.getTo();

        p.eject();
        vehicle.eject();
        vehicle.teleportAsync(destination).thenRun(() ->
                p.getScheduler().run(Arcane.getPlugin(), t -> {
                    for (Entity passenger : passengers) {
                        passenger.teleportAsync(destination).thenRun(() ->
                                p.getScheduler().run(Arcane.getPlugin(), t2 -> vehicle.addPassenger(passenger), null)
                        );
                    }
                }, null)
        );
    }

    public static void onDeath(PlayerDeathEvent e) {
        TeleportManager.saveLastLocation(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        TeleportManager.saveLastLocation(e.getPlayer());
    }
}
