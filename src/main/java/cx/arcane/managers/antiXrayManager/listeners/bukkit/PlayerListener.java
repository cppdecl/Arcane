package cx.arcane.managers.antiXrayManager.listeners.bukkit;

import cx.arcane.Arcane;
import cx.arcane.managers.antiXrayManager.AntiXrayManager;
import io.canvasmc.canvas.event.EntityTeleportAsyncEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

public class PlayerListener implements Listener {

    private void handleReset(Player player) {
        if (!player.getWorld().getName().equals("world")) return;
        AntiXrayManager.resetPlayer(player);
        AntiXrayManager.startViewCheck(player);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e)               { AntiXrayManager.stopViewCheck(e.getPlayer()); AntiXrayManager.remove(e.getPlayer()); }
    @EventHandler public void onJoin(PlayerJoinEvent e)               { handleReset(e.getPlayer()); }
    @EventHandler public void onTeleport(EntityTeleportAsyncEvent e)       {
        if (e.getEntityType() != EntityType.PLAYER) return;
        if (!(e.getEntity() instanceof Player p)) return;
        handleReset(p);
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent e)         { handleReset(e.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e){ handleReset(e.getPlayer()); }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        Location to = e.getTo();

        if (to.getWorld() == null || !to.getWorld().getName().equals("world")) return;

        int newCX = to.getBlockX() >> 4;
        int newCY = to.getBlockY() >> 4;
        int newCZ = to.getBlockZ() >> 4;

        boolean isBelowNow    = to.getBlockY() <= 0;
        boolean isBelowBefore = AntiXrayManager.isDeepslateLevel(player);

        if (isBelowNow != isBelowBefore) {
            AntiXrayManager.setDeepslateLevel(player, isBelowNow);

            if (isBelowNow) {
                AntiXrayManager.resendChunks(player, true);
                AntiXrayManager.revealNearbyChunks(player);
                AntiXrayManager.revealConnectedAirChunksAsync(player);
            }

            for (Entity nearby : player.getNearbyEntities(64, 128, 64)) {
                Location nloc = nearby.getLocation();
                AntiXrayManager.updateEntityVisibilityForPlayer(
                        player, nearby,
                        nloc.getBlockX() >> 4,
                        nloc.getBlockZ() >> 4,
                        nloc.getBlockY() <= 0
                );
            }

            return;
        }

        AntiXrayManager.updatePlayersSeeingEntity(player, player.getLocation(), newCX, newCZ, isBelowNow);

        if (isBelowNow) {
            int lastX = AntiXrayManager.getLastChunkX(player);
            int lastZ = AntiXrayManager.getLastChunkZ(player);
            int lastY = AntiXrayManager.getLastChunkY(player);

            if (newCX != lastX || newCZ != lastZ) {
                AntiXrayManager.handleChunkShift(player, lastX, lastZ, newCX, newCZ);
                AntiXrayManager.updateLastChunk(player, newCX, newCY, newCZ);

                Bukkit.getRegionScheduler().run(Arcane.getPlugin(), to, task -> {

                    int viewDistance = player.getViewDistance();

                    int playerCX = player.getLocation().getBlockX() >> 4;
                    int playerCZ = player.getLocation().getBlockZ() >> 4;

                    for (Entity entity : player.getWorld().getEntities()) {
                        Location nloc = entity.getLocation();

                        int cx = nloc.getBlockX() >> 4;
                        int cz = nloc.getBlockZ() >> 4;

                        int dx = Math.abs(cx - playerCX);
                        int dz = Math.abs(cz - playerCZ);

                        int maxDist = (int) Math.ceil(viewDistance * 1.5);

                        if (dx > maxDist || dz > maxDist) continue;

                        AntiXrayManager.updateEntityVisibilityForPlayer(
                                player, entity,
                                cx, cz,
                                nloc.getBlockY() <= 0
                        );
                    }
                });

                AntiXrayManager.revealConnectedAirChunksAsync(player);
            } else if (newCY != lastY) {
                AntiXrayManager.updateLastChunk(player, newCX, newCY, newCZ);
                AntiXrayManager.revealConnectedAirChunksAsync(player);
            }
        } else {
            AntiXrayManager.updateLastChunk(player, newCX, newCY, newCZ);
        }
    }
}