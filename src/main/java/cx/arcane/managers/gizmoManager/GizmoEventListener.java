package cx.arcane.managers.gizmoManager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

public class GizmoEventListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (!GizmoManager.isGizmo(item)) return;

        event.setCancelled(true);
    }
}
