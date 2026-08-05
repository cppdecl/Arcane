package cx.arcane.utils;

import cx.arcane.Arcane;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class LocationUtils {

    public static void teleportPoint(Player player, String name) {
        String worldName = Arcane.getPlugin().getConfig().getString("points." + name + ".world");
        if (worldName == null) {
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }

        double x = Arcane.getPlugin().getConfig().getDouble("points." + name + ".x");
        double y = Arcane.getPlugin().getConfig().getDouble("points." + name + ".y");
        double z = Arcane.getPlugin().getConfig().getDouble("points." + name + ".z");
        float yaw = (float) Arcane.getPlugin().getConfig().getDouble("points." + name + ".yaw");
        float pitch = (float) Arcane.getPlugin().getConfig().getDouble("points." + name + ".pitch");

        Location loc = new Location(world, x, y, z, yaw, pitch);

        player.teleportAsync(loc);
    }

    public static Location getPoint(String name) {
        String worldName = Arcane.getPlugin().getConfig().getString("points." + name + ".world");
        if (worldName == null) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        double x = Arcane.getPlugin().getConfig().getDouble("points." + name + ".x");
        double y = Arcane.getPlugin().getConfig().getDouble("points." + name + ".y");
        double z = Arcane.getPlugin().getConfig().getDouble("points." + name + ".z");
        float yaw = (float) Arcane.getPlugin().getConfig().getDouble("points." + name + ".yaw");
        float pitch = (float) Arcane.getPlugin().getConfig().getDouble("points." + name + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    public static Location savePoint(Location location, String name) {
        Location loc = LocationUtils.centerLocationToBlock(location);
        FileConfiguration config = Arcane.getPlugin().getConfig();

        config.set("points." + name + ".world", loc.getWorld().getName());
        config.set("points." + name + ".x", loc.getX());
        config.set("points." + name + ".y", loc.getY());
        config.set("points." + name + ".z", loc.getZ());
        config.set("points." + name + ".yaw", loc.getYaw());
        config.set("points." + name + ".pitch", loc.getPitch());

        Bukkit.getAsyncScheduler().runNow(Arcane.getPlugin(), task -> {
            Arcane.getPlugin().saveConfig();
        });

        return loc;
    }

    public static Location centerLocationToBlock(Location location) {
        return centerLocationToBlock(location, true, true);
    }

    public static Location centerLocationToBlock(Location location, boolean centerYaw, boolean centerPitch) {
        if (location == null) {
            return null;
        }

        double x = Math.floor(location.getX()) + 0.5;
        double y = Math.floor(location.getY());
        double z = Math.floor(location.getZ()) + 0.5;

        float yaw = location.getYaw();
        if (centerYaw) {
            yaw = Math.round(yaw / 90f) * 90f;
            yaw = (yaw % 360 + 360) % 360; // normalize to 0–360
        }

        float pitch = location.getPitch();
        if (centerPitch) {
            pitch = Math.round(pitch / 90f) * 90f;
            // Clamp pitch so it stays valid in Minecraft (-90 to 90)
            if (pitch > 90) pitch = 90;
            if (pitch < -90) pitch = -90;
        }

        return new Location(location.getWorld(), x, y, z, yaw, pitch);
    }

    public static Location centerYawPitch(Location location) {
        if (location == null) {
            return null;
        }
        float yaw = snapYaw(location.getYaw());
        float pitch = snapPitch(location.getPitch());
        return new Location(location.getWorld(), location.getX(), location.getY(), location.getZ(), yaw, pitch);
    }

    public static Location centerYaw(Location location) {
        if (location == null) {
            return null;
        }
        float yaw = snapYaw(location.getYaw());
        return new Location(location.getWorld(), location.getX(), location.getY(), location.getZ(), yaw, location.getPitch());
    }

    public static Location centerPitch(Location location) {
        if (location == null) {
            return null;
        }
        float pitch = snapPitch(location.getPitch());
        return new Location(location.getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), pitch);
    }

    // --- Helper methods ---
    private static float snapYaw(float yaw) {
        yaw = Math.round(yaw / 90f) * 90f;
        yaw = (yaw % 360 + 360) % 360; // normalize to 0–360
        return yaw;
    }

    private static float snapPitch(float pitch) {
        pitch = Math.round(pitch / 90f) * 90f;
        return Math.max(-90f, Math.min(90f, pitch)); // clamp to Minecraft range
    }

    public static String serializePlain(Location loc) {
        if (loc == null) return null;
        return loc.getWorld().getName() + "|" +
                loc.getX() + "|" +
                loc.getY() + "|" +
                loc.getZ() + "|" +
                loc.getPitch() + "|" +
                loc.getYaw();
    }

    public static Location deserializePlain(String str) {
        if (str == null || str.isEmpty()) return null;

        String[] parts = str.split("\\|");
        if (parts.length < 6) return null;

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;

        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        float pitch = Float.parseFloat(parts[4]);
        float yaw = Float.parseFloat(parts[5]);

        return new Location(world, x, y, z, yaw, pitch);
    }
}
