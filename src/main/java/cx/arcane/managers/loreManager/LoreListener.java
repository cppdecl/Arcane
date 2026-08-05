package cx.arcane.managers.loreManager;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import cx.arcane.Arcane;
import cx.arcane.managers.gizmoManager.GizmoManager;
import cx.arcane.managers.priceManager.PriceManager;
import dev.triumphteam.gui.GuiView;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class LoreListener implements PacketListener, Listener {

    private static final Map<UUID, Boolean> lastCursorEmpty = new HashMap<>();

    private static final NamespacedKey GUI_KEY = new NamespacedKey(Arcane.getPlugin(), "viewing_gui");

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof GuiView) {
            player.getPersistentDataContainer().set(GUI_KEY, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        lastCursorEmpty.remove(player.getUniqueId());
        player.getPersistentDataContainer().remove(GUI_KEY);
    }

    public static boolean isCursorEmpty(Player player) {
        return player.getItemOnCursor().getType() == Material.AIR;
    }

    public static void refreshIfCursorStateChanged(Player player) {
        boolean current = isCursorEmpty(player);
        Boolean previous = lastCursorEmpty.put(player.getUniqueId(), current);

        if (previous == null || previous != current) {
            player.getScheduler().runDelayed(
                    Arcane.getPlugin(), t -> player.updateInventory(), null, 2L
            );
        }
    }

    private static boolean isPacketLore(Component line) {
        String plain = PlainTextComponentSerializer.plainText().serialize(line);
        return plain.startsWith("Self Destructs")
                || plain.startsWith("Worth:")
                || plain.startsWith("Item is Destroyed")
                || plain.startsWith("(Gizmo");
    }

    private static ItemStack withVisualLore(org.bukkit.inventory.ItemStack original) {
        org.bukkit.inventory.ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return SpigotConversionUtil.fromBukkitItemStack(item);

        List<Component> lore = meta.lore() != null
                ? new ArrayList<>(Objects.requireNonNull(meta.lore()))
                : new ArrayList<>();

        lore.removeIf(LoreListener::isPacketLore);

        if (GizmoManager.isGizmo(item)) {
            lore.addAll(GizmoManager.getLore(item));
        } else {
            lore.add(PriceManager.getSellWorthTextComponent(item));
        }

        meta.lore(lore);
        item.setItemMeta(meta);

        return SpigotConversionUtil.fromBukkitItemStack(item);
    }

    private static ItemStack withoutVisualLore(org.bukkit.inventory.ItemStack original) {
        org.bukkit.inventory.ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.lore() == null) {
            return SpigotConversionUtil.fromBukkitItemStack(item);
        }

        List<Component> lore = new ArrayList<>(Objects.requireNonNull(meta.lore()));
        lore.removeIf(LoreListener::isPacketLore);

        meta.lore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);

        return SpigotConversionUtil.fromBukkitItemStack(item);
    }

    private static ItemStack processItem(org.bukkit.inventory.ItemStack bukkitItem, boolean isGizmo, boolean showLore) {
        if (isGizmo || showLore) {
            return withVisualLore(bukkitItem);
        }
        return withoutVisualLore(bukkitItem);
    }

    /* ===========================
       INVENTORY EVENTS
       =========================== */

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        player.getScheduler().runDelayed(
                Arcane.getPlugin(), t -> refreshIfCursorStateChanged(player), null, 2L
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        player.getScheduler().runDelayed(
                Arcane.getPlugin(), t -> refreshIfCursorStateChanged(player), null, 2L
        );
    }

    /* ===========================
       GAMEMODE CHANGE FIX
       =========================== */

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().runDelayed(
                Arcane.getPlugin(), t -> {
                    if (event.getNewGameMode() != GameMode.SURVIVAL) {
                        removeVisualLoreFromInventory(player);
                    } else {
                        player.updateInventory();
                    }
                }, null, 2L
        );
    }

    private void removeVisualLoreFromInventory(Player player) {
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta == null || meta.lore() == null) continue;

            List<Component> lore = new ArrayList<>(Objects.requireNonNull(meta.lore()));
            lore.removeIf(LoreListener::isPacketLore);

            meta.lore(lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
        }

        player.updateInventory();
    }

    /* ===========================
       PACKET INTERCEPTION
       =========================== */

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        boolean isSurvival = player.getGameMode() == GameMode.SURVIVAL;
        if (!isSurvival) return;

        boolean cursorEmpty = isCursorEmpty(player);
        boolean isGui = player.getPersistentDataContainer().has(GUI_KEY);

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
            ItemStack packetItem = wrapper.getItem();
            if (packetItem.isEmpty()) return;

            org.bukkit.inventory.ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(packetItem);
            if (bukkitItem == null) return;

            boolean isTopInventorySlot = wrapper.getWindowId() != 0;
            boolean isGizmo = GizmoManager.isGizmo(bukkitItem);
            boolean showLore = !isGui || !isTopInventorySlot;

            wrapper.setItem(processItem(bukkitItem, isGizmo, showLore && cursorEmpty));
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
            List<ItemStack> items = wrapper.getItems();
            if (items.isEmpty()) return;

            boolean isTopInventoryPacket = wrapper.getWindowId() != 0;
            boolean showLore = !isGui || !isTopInventoryPacket;

            List<ItemStack> modified = new ArrayList<>(items.size());

            for (ItemStack packetItem : items) {
                if (packetItem == null || packetItem.isEmpty()) {
                    modified.add(packetItem);
                    continue;
                }

                org.bukkit.inventory.ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(packetItem);
                if (bukkitItem == null) {
                    modified.add(packetItem);
                    continue;
                }

                boolean isGizmo = GizmoManager.isGizmo(bukkitItem);
                modified.add(processItem(bukkitItem, isGizmo, showLore && cursorEmpty));
            }

            wrapper.setItems(modified);
        }
    }
}