package cx.arcane.managers.pluginManager;

import com.github.retrooper.packetevents.PacketEvents;
import cx.arcane.Arcane;
import dev.triumphteam.gui.paper.PaperGuiSettings;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;

public class PluginManager {

    public static void onLoad() {
        //PacketEvents.setAPI(SpigotPacketEventsBuilder.build(Arcane.getPlugin()));
        //PacketEvents.getAPI().load();
    }

    public static void onEnable() {
        PaperGuiSettings.init(Arcane.getPlugin());
        //PacketEvents.getAPI().init();
    }

    public static void onDisable() {
        //PacketEvents.getAPI().terminate();
    }

    public static void onSave() {

    }
}
