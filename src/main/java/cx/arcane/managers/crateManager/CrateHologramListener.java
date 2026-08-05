package cx.arcane.managers.crateManager;

import de.oliver.fancyholograms.api.events.HologramDeleteEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.Location;
import org.bukkit.Bukkit;

public class CrateHologramListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHologramDelete(HologramDeleteEvent e) {
        String name = e.getHologram().getData().getName();
        if (!CrateHologramManager.isCrateHologram(name)) return;

        String crateId = CrateHologramManager.crateIdFromHologramName(name);
        if (crateId == null) return;

        CrateData crate = CrateManager.getCrateById(crateId);
        if (crate == null) return;

        Location loc = crate.getLocation();
        if (loc == null) return;

        // Recreate on next tick on the correct region
        Bukkit.getRegionScheduler().runDelayed(
                cx.arcane.Arcane.getPlugin(),
                loc,
                t -> CrateHologramManager.createHolograms(crate),
                1L
        );
    }
}