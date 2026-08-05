package cx.arcane.managers.crateManager;

import com.destroystokyo.paper.ParticleBuilder;
import com.github.retrooper.packetevents.protocol.particle.data.ParticleData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;
import cx.arcane.Arcane;

import java.util.concurrent.TimeUnit;

public class CrateParticleManager {

    public static void startParticleTask() {
        Bukkit.getAsyncScheduler().runAtFixedRate(Arcane.getPlugin(), t -> {
            for (CrateData crate : CrateManager.getCrates().values()) {
                Bukkit.getRegionScheduler().run(Arcane.getPlugin(), crate.getLocation(), ts -> {
                    spawnParticles(crate);
                });
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private static void spawnParticles(CrateData crate) {
        if (crate == null) return;
        Location loc = crate.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        Material material = crate.getLocation().getBlock().getType();
        if (!material.isBlock()) material = Material.OAK_PLANKS;

        BlockData blockData = material.createBlockData();

        for (int i = 0; i < 3; i++) {
            int finalI = i;
            Bukkit.getRegionScheduler().runDelayed(Arcane.getPlugin(), crate.getLocation(), ts -> {
                new ParticleBuilder(Particle.BLOCK)
                        .location(loc.clone().add(0.5, 0.2 + (0.3 * finalI), 0.5))
                        .count(15)
                        .offset(0.3, 0.3, 0.3)
                        .data(blockData)
                        .spawn();
            }, 1 + (2 * i));
        }
    }
}