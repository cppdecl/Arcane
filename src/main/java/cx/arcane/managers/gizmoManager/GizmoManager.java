package cx.arcane.managers.gizmoManager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import cx.arcane.Arcane;
import cx.arcane.managers.gizmoManager.listeners.FireAxeListener;
import cx.arcane.managers.gizmoManager.listeners.SellWandListener;
import cx.arcane.managers.gizmoManager.listeners.TrenchPickaxeListener;
import cx.arcane.managers.gizmoManager.listeners.TrenchShovelListener;
import cx.arcane.utils.Colors;
import cx.arcane.utils.ItemUtils;
import cx.arcane.utils.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.List;

public class GizmoManager {

    public static void duraCancel(ItemStack tool) {
        if (tool == null || tool.getType().isAir()) return;

        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Long firstUsedAt = ItemUtils.getPDC(pdc, "cit_tool_first_used_at_ms", PersistentDataType.LONG);
        Long destructionMs = ItemUtils.getPDC(pdc, "cit_tool_destruction_time_ms", PersistentDataType.LONG);

        if (firstUsedAt == null || firstUsedAt < 0 || destructionMs == null || destructionMs <= 0) return;

        long elapsed = System.currentTimeMillis() - firstUsedAt;

        // Add an offset multiplier (>1) so the tool looks damaged early
        double offsetMultiplier = 1.2; // 20% early crumbling
        double progress = Math.min(Math.max((double) elapsed / destructionMs * offsetMultiplier, 0.0), 1.0);

        int maxDurability = tool.getType().getMaxDurability();
        int damageValue = (int) (progress * maxDurability);

        // Cap damage at maxDurability - 1 to avoid auto-breaking visually before code triggers
        damageValue = Math.min(damageValue, maxDurability - 1);

        damageable.setDamage(damageValue);
        tool.setItemMeta(meta);
    }

    public static String getType() { return "tool"; }

    public static List<String> getToolTypes() {
        return List.of(
                "TrenchPickaxe",
                "TrenchShovel",
                "FireAxe",
                "MultiTool",
                "SellWand",
                "TunnelBoringMachine",
                "ThirstyBucket",
                "EntityBucket"
        );
    }

    public static List<Component> getLore(ItemStack item) {
        if (!isGizmo(item)) return List.of();

        long remainingMs = getRemainingMs(item);

        if (isExpired(item)) {
            return List.of(Component.text("Item is ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("Destroyed", Colors.HOT_PINK)).decoration(TextDecoration.ITALIC, false));
        }

        long secondsLeft = remainingMs / 1000;

        int days = (int) (secondsLeft / 86400);
        int hours = (int) ((secondsLeft % 86400) / 3600);
        int minutes = (int) ((secondsLeft % 3600) / 60);
        int seconds = (int) (secondsLeft % 60);

        StringBuilder sb = new StringBuilder();

        int[] values = {days, hours, minutes, seconds};
        String[] units = {"d", "h", "m", "s"};

        int count = 0;
        for (int i = 0; i < values.length && count < 2; i++) {
            if (values[i] > 0) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(values[i]).append(units[i]);
                count++;
            }
        }

        if (sb.isEmpty()) sb.append("0s");

        Component destructComponent = Component.text("Self Destructs", Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" in ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .append(Component.text(sb.toString(), Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false));

        if (!hasBeenUsed(item)) {
            Component note = Component.text("(Gizmo Activates At First Use)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
            return List.of(destructComponent, note);
        }

        return List.of(destructComponent);
    }

    public static long getRemainingMs(ItemStack item) {
        if (!isGizmo(item)) return 0;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Long duration = ItemUtils.getPDC(pdc, "cit_tool_destruction_time_ms", PersistentDataType.LONG);
        if (duration == null || duration <= 0) return 0;

        if (!hasBeenUsed(item)) {
            // Timer hasn’t started yet, return full duration
            return duration;
        }

        Long firstUsedAt = ItemUtils.getPDC(pdc, "cit_tool_first_used_at_ms", PersistentDataType.LONG);
        if (firstUsedAt == null || firstUsedAt < 0) return duration;

        long remaining = (firstUsedAt + duration) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }


    public static boolean isExpired(ItemStack item) {
        if (!isGizmo(item)) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return true;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Long durationMs = ItemUtils.getPDC(
                pdc,
                "cit_tool_destruction_time_ms",
                PersistentDataType.LONG
        );
        if (durationMs == null) return true;

        if (!hasBeenUsed(item)) return false;

        Long firstUsedAtMs = ItemUtils.getPDC(
                pdc,
                "cit_tool_first_used_at_ms",
                PersistentDataType.LONG
        );

        if (firstUsedAtMs == null || firstUsedAtMs < 0) return true;

        return System.currentTimeMillis() >= firstUsedAtMs + durationMs;
    }

    public static boolean hasBeenUsed(ItemStack item) {
        if (!isGizmo(item))
            return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        return Boolean.TRUE.equals(ItemUtils.getPDC(pdc, "cit_tool_has_been_used", PersistentDataType.BOOLEAN));
    }

    public static void markUsed(ItemStack item) {
        if (!isGizmo(item)) return;

        duraCancel(item);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Boolean hasBeenUsed = ItemUtils.getPDC(
                pdc,
                "cit_tool_has_been_used",
                PersistentDataType.BOOLEAN
        );

        if (Boolean.TRUE.equals(hasBeenUsed)) return;

        ItemUtils.SetPDC(pdc, "cit_tool_has_been_used", PersistentDataType.BOOLEAN, true);
        ItemUtils.SetPDC(pdc, "cit_tool_first_used_at_ms", PersistentDataType.LONG, Instant.now().toEpochMilli());

        item.setItemMeta(meta);
    }

    public static boolean isGizmo(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        return "tool".equals(ItemUtils.getPDC(pdc, "cit_type", PersistentDataType.STRING));
    }

    public static String getToolType(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "";
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        return ItemUtils.getPDC(pdc, "cit_tool_type", PersistentDataType.STRING);
    }

    public static void onEnable() {
        Bukkit.getPluginManager().registerEvents(new GizmoEventListener(), Arcane.getPlugin());
        PacketEvents.getAPI().getEventManager().registerListener(new GizmoPacketListener(), PacketListenerPriority.HIGHEST);

        Bukkit.getPluginManager().registerEvents(new TrenchPickaxeListener(), Arcane.getPlugin());
        Bukkit.getPluginManager().registerEvents(new FireAxeListener(), Arcane.getPlugin());
        Bukkit.getPluginManager().registerEvents(new SellWandListener(), Arcane.getPlugin());
        Bukkit.getPluginManager().registerEvents(new TrenchShovelListener(), Arcane.getPlugin());

    }

    public static void onDisable() {}

    public static void onSave() {

    }

    public static class TrenchPickaxe {
        public static long getDestructionTimeMs() { return 3 * 24 * 60 * 60 * 1000L; }
        public static String getToolType() { return "trenchpickaxe"; }
        public static ItemStack createItem() {
            return createItem(getDestructionTimeMs());
        }
        public static ItemStack createItem(long durationMs) {

            ItemStack stack = new ItemStack(Material.NETHERITE_PICKAXE);
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) return stack;

            meta.displayName(
                    Text.toSmallCapsComponent(
                            "Trench Pickaxe"
                    ).color(Colors.HOT_PINK)
            );

            meta.addEnchant(Enchantment.EFFICIENCY, 255, true);

            glow(meta);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            ItemUtils.SetPDC(pdc, "cit_type", PersistentDataType.STRING, getType());
            ItemUtils.SetPDC(pdc, "cit_tool_type", PersistentDataType.STRING, getToolType());
            ItemUtils.SetPDC(pdc, "cit_tool_created_at_ms", PersistentDataType.LONG, Instant.now().toEpochMilli());
            ItemUtils.SetPDC(pdc, "cit_tool_first_used_at_ms", PersistentDataType.LONG, -1L);
            ItemUtils.SetPDC(pdc, "cit_tool_has_been_used", PersistentDataType.BOOLEAN, false);
            ItemUtils.SetPDC(pdc, "cit_tool_use_count", PersistentDataType.LONG, 0L);
            ItemUtils.SetPDC(pdc, "cit_tool_max_use_count", PersistentDataType.LONG, 0L);
            ItemUtils.SetPDC(pdc, "cit_tool_destruction_time_ms", PersistentDataType.LONG, durationMs);

            stack.setItemMeta(meta);

            return stack;
        }
    }

    public static class TrenchShovel {
        public static long getDestructionTimeMs() { return 3 * 24 * 60 * 60 * 1000L; }
        public static String getToolType() { return "trenchshovel"; }
        public static ItemStack createItem() {
            return createItem(getDestructionTimeMs());
        }
        public static ItemStack createItem(long durationMs) {

            ItemStack stack = new ItemStack(Material.NETHERITE_SHOVEL);
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) return stack;

            meta.displayName(
                    Text.toSmallCapsComponent(
                            "Trench Shovel"
                    ).color(Colors.HOT_PINK)
            );

            meta.addEnchant(Enchantment.EFFICIENCY, 255, true);

            glow(meta);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            ItemUtils.SetPDC(pdc, "cit_type", PersistentDataType.STRING, getType());
            ItemUtils.SetPDC(pdc, "cit_tool_type", PersistentDataType.STRING, getToolType());
            ItemUtils.SetPDC(pdc, "cit_tool_created_at_ms", PersistentDataType.LONG, Instant.now().toEpochMilli());
            ItemUtils.SetPDC(pdc, "cit_tool_first_used_at_ms", PersistentDataType.LONG, -1L);
            ItemUtils.SetPDC(pdc, "cit_tool_has_been_used", PersistentDataType.BOOLEAN, false);
            ItemUtils.SetPDC(pdc, "cit_tool_use_count", PersistentDataType.LONG, 0L);
            ItemUtils.SetPDC(pdc, "cit_tool_max_use_count", PersistentDataType.LONG, 0L);
            ItemUtils.SetPDC(pdc, "cit_tool_destruction_time_ms", PersistentDataType.LONG, durationMs);

            stack.setItemMeta(meta);

            return stack;
        }
    }

    public static class FireAxe {
        public static long getDestructionTimeMs() { return 3 * 24 * 60 * 60 * 1000L; }
        public static String getToolType() { return "fireaxe"; }
        public static ItemStack createItem() {
            return createItem(getDestructionTimeMs());
        }
        public static ItemStack createItem(long durationMs) {

            ItemStack stack = new ItemStack(Material.NETHERITE_AXE);
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) return stack;

            meta.displayName(
                    Text.toSmallCapsComponent(
                            "Fire Axe"
                    ).color(Colors.HOT_PINK)
            );

            meta.addEnchant(Enchantment.EFFICIENCY, 255, true);

            glow(meta);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            ItemUtils.SetPDC(pdc, "cit_type", PersistentDataType.STRING, getType());
            ItemUtils.SetPDC(pdc, "cit_tool_type", PersistentDataType.STRING, getToolType());
            ItemUtils.SetPDC(pdc, "cit_tool_created_at_ms", PersistentDataType.LONG, Instant.now().toEpochMilli());
            ItemUtils.SetPDC(pdc, "cit_tool_first_used_at_ms", PersistentDataType.LONG, -1L);
            ItemUtils.SetPDC(pdc, "cit_tool_has_been_used", PersistentDataType.BOOLEAN, false);
            ItemUtils.SetPDC(pdc, "cit_tool_use_count", PersistentDataType.LONG, 0L);
            ItemUtils.SetPDC(pdc, "cit_tool_max_use_count", PersistentDataType.LONG, 0L);
            ItemUtils.SetPDC(pdc, "cit_tool_destruction_time_ms", PersistentDataType.LONG, durationMs);

            stack.setItemMeta(meta);

            return stack;
        }
    }

    public static class SellWand {
        public static long getDestructionTimeMs() { return 3 * 24 * 60 * 60 * 1000L; }
        public static String getToolType() { return "sellwand"; }
        public static ItemStack createItem() {
            return createItem(getDestructionTimeMs());
        }
        public static ItemStack createItem(long durationMs) {

            ItemStack stack = new ItemStack(Material.NETHERITE_AXE);
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) return stack;

            meta.displayName(
                    Text.toSmallCapsComponent(
                            "Sell Wand"
                    ).color(Colors.HOT_PINK)
            );

            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);

            glow(meta);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            ItemUtils.SetPDC(pdc, "cit_type", PersistentDataType.STRING, getType());
            ItemUtils.SetPDC(pdc, "cit_tool_type", PersistentDataType.STRING, getToolType());
            ItemUtils.SetPDC(pdc, "cit_tool_created_at_ms", PersistentDataType.LONG, Instant.now().toEpochMilli());
            ItemUtils.SetPDC(pdc, "cit_tool_first_used_at_ms", PersistentDataType.LONG, -1L);
            ItemUtils.SetPDC(pdc, "cit_tool_has_been_used", PersistentDataType.BOOLEAN, false);
            ItemUtils.SetPDC(pdc, "cit_tool_use_count", PersistentDataType.LONG, 0L);
            ItemUtils.SetPDC(pdc, "cit_tool_max_use_count", PersistentDataType.LONG, 0L);
            ItemUtils.SetPDC(pdc, "cit_tool_destruction_time_ms", PersistentDataType.LONG, durationMs);

            stack.setItemMeta(meta);

            return stack;
        }
    }

    private static void glow(ItemMeta meta) {
        meta.addEnchant(Enchantment.BLAST_PROTECTION, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
}
