package cx.arcane.managers.loreManager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import cx.arcane.Arcane;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public class LoreManager {

    private static ScheduledTask tickWorker;

    public static void onEnable() {
        LoreListener listener = new LoreListener();
        PacketEvents.getAPI().getEventManager().registerListener(listener, PacketListenerPriority.HIGHEST);
        Arcane.getPlugin().getServer().getPluginManager().registerEvents(listener, Arcane.getPlugin());
        startTickWorker();
    }

    public static void startTickWorker() {
        if (tickWorker != null && !tickWorker.isCancelled()) return;

        tickWorker = Bukkit.getAsyncScheduler().runAtFixedRate(
                Arcane.getPlugin(),
                t -> {
                    for (PlayerData pData : PlayerManager.getOnline()) {
                        Player player = pData.getPlayer();
                        if (player.getGameMode() != GameMode.SURVIVAL) continue;
                        if (!LoreListener.isCursorEmpty(player)) continue;
                        player.updateInventory();
                    }
                },
                1,
                1, TimeUnit.SECONDS
        );
    }

    public static void onDisable() {
        if (tickWorker != null) {
            tickWorker.cancel();
            tickWorker = null;
        }
    }

    public static void onSave() {

    }
}