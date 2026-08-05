package cx.arcane.utils;

import cx.arcane.Arcane;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class ItemUtils {

    private ItemUtils() {}

    public static String getEffectiveName(ItemStack item) {
        if (item == null || item.getType().isAir()) return "Air";
        Component component = item.effectiveName();
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String getEffectiveName(Material material) {
        if (material.isAir() || !material.isItem()) return "Air";
        ItemStack stack = ItemStack.of(material);
        return getEffectiveName(stack);
    }

    public static List<String> getAllEffectiveNames() {
        return Registry.MATERIAL.stream()
                .filter(mat -> mat.isItem() && !mat.isAir() && !mat.isLegacy())
                .map(ItemUtils::getEffectiveName)
                .collect(Collectors.toList());
    }

    public static Map<Material, String> getAllEffectiveNameMap() {
        return Registry.MATERIAL.stream()
                .filter(mat -> mat.isItem() && !mat.isAir() && !mat.isLegacy())
                .collect(Collectors.toMap(m -> m, ItemUtils::getEffectiveName));
    }

    public static boolean hasInventory(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm)) return false;

        return bsm.getBlockState() instanceof InventoryHolder;
    }

    public static ItemStack[] getContainerContents(ItemStack item) {
        if (!hasInventory(item)) return new ItemStack[0];

        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        BlockState state = meta.getBlockState();

        InventoryHolder holder = (InventoryHolder) state;
        return holder.getInventory().getContents();
    }

    public static void setContainerContents(ItemStack item, ItemStack[] contents) {
        if (!hasInventory(item)) return;

        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        BlockState state = meta.getBlockState();

        InventoryHolder holder = (InventoryHolder) state;
        holder.getInventory().setContents(contents);

        meta.setBlockState(state);
        item.setItemMeta(meta);
    }


    /**
     * Search items by effective name (case-insensitive substring match).
     *
     * @param query search string
     * @return list of matching Materials
     */
    public static List<Material> searchByName(String query) {
        if (query == null || query.isEmpty()) return Collections.emptyList();
        String lower = query.toLowerCase();
        return Registry.MATERIAL.stream()
                .filter(mat -> mat.isItem() && !mat.isAir() && !mat.isLegacy())
                .filter(mat -> getEffectiveName(mat).toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    public static List<Material> searchByKey(String key) {
        if (key == null || key.isEmpty()) return Collections.emptyList();
        String lower = key.toLowerCase();
        return Registry.MATERIAL.stream()
                .filter(mat -> mat.isItem() && !mat.isAir() && !mat.isLegacy())
                .filter(mat -> Objects.requireNonNull(mat.getItemTranslationKey()).toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    /**
     * Find the first item matching the name (case-insensitive exact match).
     *
     * @param name the name to match
     * @return optional Material
     */
    public static Optional<Material> findByExactName(String name) {
        if (name == null || name.isEmpty()) return Optional.empty();
        String lower = name.toLowerCase();
        return Registry.MATERIAL.stream()
                .filter(mat -> mat.isItem() && !mat.isAir() && !mat.isLegacy())
                .filter(mat -> getEffectiveName(mat).toLowerCase().equals(lower))
                .findFirst();
    }

    public static boolean matchesQuery(ItemStack item, String query) {
        if (query == null || query.isEmpty()) return true;
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        String displayName = PlainTextComponentSerializer.plainText().serialize(item.effectiveName()).toLowerCase(Locale.ROOT);

        return displayName.contains(lowerQuery);
    }

    public static ItemCategory getCategory(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return ItemCategory.ALL;
        }

        Material mat = item.getType();

        // Blocks
        if (mat.isBlock()) {
            return ItemCategory.BLOCKS;
        }

        // Tools
        if (mat.name().endsWith("_PICKAXE") ||
                mat.name().endsWith("_AXE") ||
                mat.name().endsWith("_SHOVEL") ||
                mat.name().endsWith("_HOE") ||
                mat == Material.SHEARS ||
                mat == Material.FISHING_ROD ||
                mat == Material.FLINT_AND_STEEL) {
            return ItemCategory.TOOLS;
        }

        // Food
        if (mat.isEdible()) {
            return ItemCategory.FOOD;
        }

        // Combat
        if (mat.name().endsWith("_SWORD") ||
                mat.name().endsWith("_HELMET") ||
                mat.name().endsWith("_CHESTPLATE") ||
                mat.name().endsWith("_LEGGINGS") ||
                mat.name().endsWith("_BOOTS") ||
                mat == Material.BOW ||
                mat == Material.CROSSBOW ||
                mat == Material.SHIELD ||
                mat == Material.TRIDENT) {
            return ItemCategory.COMBAT;
        }

        // Potions
        if (mat == Material.POTION ||
                mat == Material.SPLASH_POTION ||
                mat == Material.LINGERING_POTION ||
                mat == Material.TIPPED_ARROW) {
            return ItemCategory.POTIONS;
        }

        // Books
        if (mat == Material.BOOK ||
                mat == Material.ENCHANTED_BOOK ||
                mat == Material.WRITABLE_BOOK ||
                mat == Material.WRITTEN_BOOK) {
            return ItemCategory.BOOKS;
        }

        // Ingredients
        if (mat == Material.BLAZE_ROD ||
                mat == Material.BLAZE_POWDER ||
                mat == Material.MAGMA_CREAM ||
                mat == Material.GHAST_TEAR ||
                mat == Material.ENDER_PEARL ||
                mat == Material.ENDER_EYE ||
                mat == Material.NETHER_WART ||
                mat == Material.PHANTOM_MEMBRANE) {
            return ItemCategory.INGREDIENTS;
        }

        // Utilities (default catch-all for items that aren't blocks, tools, or weapons)
        return ItemCategory.UTILITIES;
    }

    public static void logItemDiff(ItemStack a, ItemStack b) {
        String label = "Item Comparison";
        Logger logger = Arcane.getPlugin().getLogger();

        if (a == null && b == null) {
            logger.info(label + " -> both items are null");
            return;
        }
        if (a == null) {
            logger.info(label + " -> A=null, B=" + b);
            return;
        }
        if (b == null) {
            logger.info(label + " -> A=" + a + ", B=null");
            return;
        }

        logger.info(label + " -> Comparing items:");
        logger.info("  A=" + a.getType() + " x" + a.getAmount());
        logger.info("  B=" + b.getType() + " x" + b.getAmount());

        if (a.getType() != b.getType()) {
            logger.info("  ❌ Different types: " + a.getType() + " vs " + b.getType());
        }
        if (a.getAmount() != b.getAmount()) {
            logger.info("  ⚠️ Different amounts: " + a.getAmount() + " vs " + b.getAmount());
        }

        ItemMeta am = a.getItemMeta();
        ItemMeta bm = b.getItemMeta();

        if (am == null && bm == null) {
            logger.info("  ✅ No item meta on either");
            return;
        }
        if (am == null) {
            logger.info("  ❌ A has no meta, B has meta: " + bm);
            return;
        }
        if (bm == null) {
            logger.info("  ❌ A has meta: " + am + ", B has none");
            return;
        }

        // Display Name
        if (!Objects.equals(am.getDisplayName(), bm.getDisplayName())) {
            logger.info("  ❌ Different display names: '" + am.getDisplayName() + "' vs '" + bm.getDisplayName() + "'");
        }

        // Lore
        if (!Objects.equals(am.getLore(), bm.getLore())) {
            logger.info("  ❌ Different lore: " + am.getLore() + " vs " + bm.getLore());
        }

        // CustomModelData
        if (am.hasCustomModelData() || bm.hasCustomModelData()) {
            Integer amd = am.hasCustomModelData() ? am.getCustomModelData() : null;
            Integer bmd = bm.hasCustomModelData() ? bm.getCustomModelData() : null;
            if (!Objects.equals(amd, bmd)) {
                logger.info("  ❌ Different custom model data: " + amd + " vs " + bmd);
            }
        }

        // Enchantments
        if (!am.getEnchants().equals(bm.getEnchants())) {
            logger.info("  ❌ Different enchants: " + am.getEnchants() + " vs " + bm.getEnchants());
        }

        // Flags
        if (!am.getItemFlags().equals(bm.getItemFlags())) {
            logger.info("  ❌ Different item flags: " + am.getItemFlags() + " vs " + bm.getItemFlags());
        }

        // If identical
        if (a.isSimilar(b)) {
            logger.info("  ✅ Items are similar (isSimilar=true)");
        } else {
            logger.info("  ❌ Items are NOT similar (isSimilar=false)");
        }
    }


    public static boolean isAdminMaterial(Material mat) {
        return switch (mat) {
            case BEDROCK,
                 COMMAND_BLOCK,
                 CHAIN_COMMAND_BLOCK,
                 REPEATING_COMMAND_BLOCK,
                 COMMAND_BLOCK_MINECART,
                 JIGSAW,
                 STRUCTURE_BLOCK,
                 STRUCTURE_VOID,
                 BARRIER,
                 DEBUG_STICK,
                 LIGHT,
                 TEST_BLOCK,
                 TEST_INSTANCE_BLOCK
                    -> true;
            default -> false;
        };
    }

    public static List<Enchantment> getCompatibleEnchantments(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Collections.emptyList();
        }

        return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT).stream()
                .filter(enchantment -> enchantment.canEnchantItem(item))
                .collect(Collectors.toList());
    }

    public static boolean isEnchantable(ItemStack item) {
        return !getCompatibleEnchantments(item).isEmpty();
    }

    public static <P, C> void SetPDC(PersistentDataContainer pdc, String key, @NotNull PersistentDataType<P, C> type, @NotNull C value) {
        NamespacedKey nsKey = new NamespacedKey(Arcane.getPlugin(), key);
        pdc.set(nsKey, type, value);
    }

    public static boolean hasPDC(PersistentDataContainer pdc, String key) {
        NamespacedKey nsKey = new NamespacedKey(Arcane.getPlugin(), key);
        return pdc.has(nsKey);
    }

    public static  <P, C> @Nullable C getPDC(PersistentDataContainer pdc, String key, PersistentDataType<P, C> type) {
        if (!ItemUtils.hasPDC(pdc, key))
            return null;

        NamespacedKey nsKey = new NamespacedKey(Arcane.getPlugin(), key);
        return pdc.get(nsKey, type);
    }

    public static <P, C> void CopyPDC(PersistentDataContainer fromPdc, PersistentDataContainer toPdc, String key, PersistentDataType<P, C> type) {
        if (!ItemUtils.hasPDC(fromPdc, key))
            return;

        ItemUtils.SetPDC(toPdc, key, type, Objects.requireNonNull(ItemUtils.getPDC(fromPdc, key, type)));
    }

    public static String itemsToString(ItemStack[] stacks) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);

            // number of slots
            data.writeInt(stacks.length);

            for (ItemStack item : stacks) {
                if (item == null) {
                    data.writeInt(0);
                    continue;
                }

                byte[] bytes = item.serializeAsBytes();
                data.writeInt(bytes.length);
                data.write(bytes);
            }

            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ItemStack array", e);
        }
    }

    public static ItemStack[] stringToItems(String data) {
        try {
            byte[] raw = Base64.getDecoder().decode(data);
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));

            int size = in.readInt();
            ItemStack[] items = new ItemStack[size];

            for (int i = 0; i < size; i++) {
                int len = in.readInt();
                if (len == 0) {
                    items[i] = null;
                    continue;
                }

                byte[] itemBytes = in.readNBytes(len);
                items[i] = ItemStack.deserializeBytes(itemBytes);
            }

            return items;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize ItemStack array", e);
        }
    }

    public static String itemToString(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack stringToItem(String data) {
        byte[] byteArray = Base64.getDecoder().decode(data);
        return ItemStack.deserializeBytes(byteArray);
    }
}

