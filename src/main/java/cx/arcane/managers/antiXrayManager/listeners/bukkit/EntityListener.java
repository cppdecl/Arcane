package cx.arcane.managers.antiXrayManager.listeners.bukkit;

import cx.arcane.managers.antiXrayManager.AntiXrayManager;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class EntityListener implements Listener {

    @EventHandler
    public void onMove(EntityMoveEvent e) {
        Location to = e.getTo();
        if (to.getWorld() == null || !to.getWorld().getName().equals("world")) return;

        Location from = e.getFrom();
        LivingEntity entity = e.getEntity();

        boolean isBelowBefore = from.getBlockY() <= 0;
        boolean isBelowNow    = to.getBlockY() <= 0;

        int cxNow = to.getBlockX() >> 4;
        int czNow = to.getBlockZ() >> 4;

        boolean crossedYLine   = isBelowBefore != isBelowNow;
        boolean crossedChunk   = isBelowNow && (cxNow != (from.getBlockX() >> 4) || czNow != (from.getBlockZ() >> 4));

        if (crossedYLine || crossedChunk)
            AntiXrayManager.updatePlayersSeeingEntity(entity, to, cxNow, czNow, isBelowNow);
    }
}