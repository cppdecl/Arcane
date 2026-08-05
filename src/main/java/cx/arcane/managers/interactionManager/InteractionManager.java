package cx.arcane.managers.interactionManager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import cx.arcane.Arcane;
import cx.arcane.managers.authManager.listeners.AuthPacketListener;
import cx.arcane.managers.interactionManager.crystals.CrystalTracker;
import cx.arcane.managers.interactionManager.listeners.InteractionListener;
import cx.arcane.managers.interactionManager.listeners.InteractionPacketListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.concurrent.ConcurrentHashMap;

public class InteractionManager {

    public static void onEnable() {
        Bukkit.getPluginManager().registerEvents(new InteractionListener(), Arcane.getPlugin());
        PacketEvents.getAPI().getEventManager().registerListener(new InteractionPacketListener(), PacketListenerPriority.LOWEST);

        CrystalTracker.onEnable();
    }

    public static void onDisable() {
        CrystalTracker.onDisable();
    }

    public static void onSave() {

    }
}
