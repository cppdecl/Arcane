package cx.arcane.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import cx.arcane.Arcane;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.folia.AsyncScheduler;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import io.papermc.paper.threadedregions.scheduler.FoliaAsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerUtils {

    public static void sendPacketMessage(Player p, Component text) {
        WrapperPlayServerSystemChatMessage packet = new WrapperPlayServerSystemChatMessage(false, text);
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(p, packet);
    }

    public static void sendPacketActionBar(Player p, Component text) {
        WrapperPlayServerActionBar packet = new WrapperPlayServerActionBar(text);
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(p, packet);
    }

    public static void sendPacketTitle(Player p, Title title) {
        WrapperPlayServerSetTitleText titleTextPacket = new WrapperPlayServerSetTitleText(title.title());
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(p, titleTextPacket);

        WrapperPlayServerSetTitleSubtitle titleTextSubtitle = new WrapperPlayServerSetTitleSubtitle(title.subtitle());
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(p, titleTextSubtitle);

        if (title.times() != null) {
            int fadeInTicks  = (int) Math.ceil(title.times().fadeIn().toMillis() / 50.0);
            int stayTicks    = (int) Math.ceil(title.times().stay().toMillis() / 50.0);
            int fadeOutTicks = (int) Math.ceil(title.times().fadeOut().toMillis() / 50.0);

            WrapperPlayServerSetTitleTimes titleTimes = new WrapperPlayServerSetTitleTimes(
                    fadeInTicks,
                    stayTicks,
                    fadeOutTicks
            );

            PacketEvents.getAPI().getPlayerManager().sendPacketSilently(p, titleTimes);
        }
    }

    public static void sendPacketPotionEffect(Player p, PotionEffectType type, int duration, int amplifier) {
        WrapperPlayServerEntityEffect packet = new WrapperPlayServerEntityEffect(p.getEntityId(), SpigotConversionUtil.fromBukkitPotionEffectType(type), amplifier, duration * 20, (byte) 0);
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(p, packet);
    }

    public static void sendPacketRemovePotionEffect(Player p, PotionEffectType type) {
        WrapperPlayServerRemoveEntityEffect packet = new WrapperPlayServerRemoveEntityEffect(p.getEntityId(), SpigotConversionUtil.fromBukkitPotionEffectType(type));
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(p, packet);
    }

    public static boolean isOfflineUUID(UUID uuid, String username) {
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
        return offline.equals(uuid);
    }

    public static UUID generateOfflineUUID(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
    }

    public static boolean isPremiumUUID(UUID uuid, String username) {
        return uuid != null && uuid.version() == 4;
    }

    public static boolean canFitInventory(Player player, ItemStack stack) {
        Inventory inv = player.getInventory();
        int space = 0;

        for (ItemStack item : inv.getStorageContents()) {
            if (item == null) {
                space += stack.getMaxStackSize();
            } else if (item.isSimilar(stack)) {
                space += stack.getMaxStackSize() - item.getAmount();
            }
        }

        return space >= stack.getAmount();
    }

    public static boolean canFitInventory(Player player, ItemStack... stacks) {
        for (ItemStack stack : stacks) {
            if (!canFitInventory(player, stack)) {
                return false;
            }
        }
        return true;
    }


    public static boolean canFitInventory(Player player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!canFitInventory(player, stack)) {
                return false;
            }
        }
        return true;
    }


    public static void giveOrDrop(Player p, Collection<ItemStack> items) {
        ItemStack[] arr = items.toArray(new ItemStack[0]);
        for (ItemStack leftover : p.getInventory().addItem(arr).values())
            p.getWorld().dropItemNaturally(p.getLocation(), leftover);
    }

    public static void giveOrDrop(Player p, ItemStack item) {
        for (ItemStack leftover : p.getInventory().addItem(item).values())
            p.getWorld().dropItemNaturally(p.getLocation(), leftover);
    }

    public static void giveOrDrop(Player p, ItemStack item, long amount) {
        for (int i = 0; i < amount; i++)
            for (ItemStack leftover : p.getInventory().addItem(item).values())
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
    }

    public static boolean isValidName(String username) {
        int len = username.length();
        if (len < 3 || len > 16) return false;

        for (int i = 0; i < len; i++) {
            char c = username.charAt(i);
            if (!(c >= 'A' && c <= 'Z') &&
                    !(c >= 'a' && c <= 'z') &&
                    !(c >= '0' && c <= '9') &&
                    c != '_') {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidBedrockName(String username) {
        int len = username.length();

        // 16 chars max still, . as first char, and _ for spaces

        if (len < 4 || len > 16) return false;
        for (int i = 0; i < len; i++) {
            char c = username.charAt(i);
            if (!(c >= 'A' && c <= 'Z') &&
                    !(c >= 'a' && c <= 'z') &&
                    !(c >= '0' && c <= '9') &&
                    c != '_' && c != '.') {
                return false;
            }
        }

        return true;
    }

    public static File getPlayerDataFile(UUID uuid) {
        World world = Bukkit.getWorlds().getFirst();
        File playerDataFolder = new File(world.getWorldFolder(), "playerdata");
        return new File(playerDataFolder, uuid.toString() + ".dat");
    }

    public static File getPlayerDataBackupFile(UUID uuid) {
        World world = Bukkit.getWorlds().getFirst();
        File playerDataFolder = new File(world.getWorldFolder(), "playerdata");
        return new File(playerDataFolder, uuid.toString() + ".dat_old");
    }

    public static void deletePlayerDataAsync(UUID playerUuid) {
        File file = getPlayerDataFile(playerUuid);
        File fileBak = getPlayerDataBackupFile(playerUuid);

        AsyncScheduler scheduler = FoliaScheduler.getAsyncScheduler();

        scheduler.runDelayed(Arcane.getPlugin(), t -> {
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    Log.info("Deleted player data for " + playerUuid);
                } else {
                    Log.warn("Failed to delete player data for " + playerUuid);
                }
            } else {
                Log.info("Player data not found: {}", file.toString());
            }

            if (fileBak.exists()) {
                boolean deleted = fileBak.delete();
            }
        }, 2, TimeUnit.SECONDS);
    }

    public static <T> T getMeta(@NotNull Player player, @NotNull String key, @NotNull T defaultValue) {
        return player.getMetadata(Arcane.getPlugin().getName() + ":" + key).stream()
                .filter(meta -> meta.getOwningPlugin().getName().equals(Arcane.getPlugin().getName()))
                .findFirst()
                .map(MetadataValue::value)
                .map(value -> (T) value)
                .orElse(defaultValue);
    }

    public static void setMeta(@NotNull Player player, @NotNull String key, @NotNull Object value) {
        player.removeMetadata(Arcane.getPlugin().getName() + ":" + key, Arcane.getPlugin());
        player.setMetadata(Arcane.getPlugin().getName() + ":" + key, new FixedMetadataValue(Arcane.getPlugin(), value));
    }
}
