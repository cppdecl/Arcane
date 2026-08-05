package cx.arcane.utils;

import cx.arcane.Arcane;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class BareInventoryGUI {

    private static BareInventoryListener listener;
    private static final String META_KEY = "ArcLib:GUI:HideWorthLore";

    private Inventory inventory;
    private final UUID guiId;

    private Player viewer;
    private String title = "Inventory";

    private boolean hideWorthLore = false;

    // Event consumers
    private Consumer<InventoryClickEvent> clickHandler;
    private Consumer<InventoryCloseEvent> closeHandler;
    private Consumer<InventoryOpenEvent> openHandler;
    private Consumer<InventoryDragEvent> dragHandler;

    // --- Constructor ---
    public BareInventoryGUI() {
        if (listener == null) {
            listener = new BareInventoryListener(Arcane.getPlugin());
        }
        this.guiId = UUID.randomUUID();
        this.inventory = Bukkit.createInventory(new GUIHolder(), 9, title);
    }

    // --- Builder-style API ---

    public BareInventoryGUI title(String title) {
        this.title = title;
        rebuildInventory();
        return this;
    }

    public BareInventoryGUI rows(int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Rows must be between 1 and 6.");
        }
        this.inventory = Bukkit.createInventory(new GUIHolder(), rows * 9, title);
        return this;
    }

    public BareInventoryGUI inventory(Inventory inv) {
        if (inv != null) {
            this.inventory = inv;
        }
        return this;
    }

    public BareInventoryGUI hideWorthLore() {
        this.hideWorthLore = true;
        return this;
    }

    public BareInventoryGUI onClick(Consumer<InventoryClickEvent> handler) {
        this.clickHandler = handler;
        return this;
    }

    public BareInventoryGUI onClose(Consumer<InventoryCloseEvent> handler) {
        this.closeHandler = handler;
        return this;
    }

    public BareInventoryGUI onOpen(Consumer<InventoryOpenEvent> handler) {
        this.openHandler = handler;
        return this;
    }

    public BareInventoryGUI onDrag(Consumer<InventoryDragEvent> handler) {
        this.dragHandler = handler;
        return this;
    }

    public void open(Player player) {
        this.viewer = player;
        BareInventoryListener.registerInstance(this);

        if (hideWorthLore) {
            player.removeMetadata(META_KEY, Arcane.getPlugin());
            player.setMetadata(META_KEY, new FixedMetadataValue(Arcane.getPlugin(), true));
        }

        player.openInventory(inventory);
    }

    public void destroy() {
        BareInventoryListener.unregisterInstance(this);

        if (viewer != null) {
            viewer.removeMetadata(META_KEY, Arcane.getPlugin());
            if (viewer.getOpenInventory().getTopInventory().equals(inventory)) {
                viewer.closeInventory();
            }
        }
    }

    public UUID getGuiId() {
        return guiId;
    }

    private void rebuildInventory() {
        Inventory newInv = Bukkit.createInventory(new GUIHolder(), inventory.getSize(), title);
        newInv.setContents(inventory.getContents());
        this.inventory = newInv;

        if (viewer != null && viewer.isOnline()) {
            viewer.openInventory(newInv);
        }
    }

    // --- Event triggers ---
    private void handleClick(InventoryClickEvent e) {
        if (clickHandler != null) clickHandler.accept(e);
    }

    private void handleClose(InventoryCloseEvent e) {
        if (closeHandler != null) closeHandler.accept(e);
    }

    private void handleOpen(InventoryOpenEvent e) {
        if (openHandler != null) openHandler.accept(e);
    }

    private void handleDrag(InventoryDragEvent e) {
        if (dragHandler != null) dragHandler.accept(e);
    }

    public Inventory getInventory() {
        return inventory;
    }

    // --- Holder ---
    private static class GUIHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    // --- Listener ---
    public static class BareInventoryListener implements Listener {

        private static final Map<UUID, BareInventoryGUI> activeGuis = new HashMap<>();

        public BareInventoryListener(Plugin plugin) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }

        public static void registerInstance(BareInventoryGUI gui) {
            activeGuis.put(gui.getGuiId(), gui);
        }

        public static void unregisterInstance(BareInventoryGUI gui) {
            activeGuis.remove(gui.getGuiId());
        }

        private BareInventoryGUI resolve(InventoryEvent e) {
            if (e.getInventory().getHolder() instanceof GUIHolder) {
                for (BareInventoryGUI gui : activeGuis.values()) {
                    if (gui.getInventory().equals(e.getInventory())) {
                        return gui;
                    }
                }
            }
            return null;
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onClick(InventoryClickEvent e) {
            BareInventoryGUI gui = resolve(e);
            if (gui != null) gui.handleClick(e);
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onDrag(InventoryDragEvent e) {
            BareInventoryGUI gui = resolve(e);
            if (gui != null) gui.handleDrag(e);
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onClose(InventoryCloseEvent e) {
            BareInventoryGUI gui = resolve(e);
            if (gui != null) {
                gui.handleClose(e);

                if (e.getPlayer() instanceof Player player) {
                    player.removeMetadata(META_KEY, Arcane.getPlugin());
                }

                gui.destroy();
            }
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onOpen(InventoryOpenEvent e) {
            BareInventoryGUI gui = resolve(e);
            if (gui != null) gui.handleOpen(e);
        }
    }
}
