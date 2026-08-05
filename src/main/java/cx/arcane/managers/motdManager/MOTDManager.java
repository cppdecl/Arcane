package cx.arcane.managers.motdManager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import cx.arcane.managers.authManager.listeners.AuthPacketListener;

public class MOTDManager {
    public static void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(new MOTDPacketListener(), PacketListenerPriority.MONITOR);
    }

    public static void onDisable() {

    }

    public static void onSave() {

    }
}
