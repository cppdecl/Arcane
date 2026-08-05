package cx.arcane.managers.placeholderManager;

import org.bukkit.Bukkit;

public class PlaceholderManager {
    private static boolean enabled = false;

    private static boolean isEnabled() {
        return enabled;
    }

    public static void onEnable() {
        boolean pluginFound = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        boolean pluginEnabled = pluginFound && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        if (pluginFound && pluginEnabled) {
            new ArcanePlaceholderExpansion().register();
            enabled = true;
        } else {
            enabled = false;
        }
    }

    public static void onDisable() {

    }

    public static void onSave() {

    }
}
