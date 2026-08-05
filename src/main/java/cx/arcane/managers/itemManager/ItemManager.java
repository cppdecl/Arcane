package cx.arcane.managers.itemManager;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ItemManager {

    private static final ConcurrentHashMap<String, Material> nameToMat = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Material> keyToMat  = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Material, String> matToName = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Material, String> matToKey  = new ConcurrentHashMap<>();
    private static volatile List<String> sortedNames = Collections.emptyList();

    public static void onEnable() {
        Registry.MATERIAL.stream()
                .filter(m -> m.isItem() && !m.isAir() && !m.isLegacy())
                .forEach(m -> {
                    String displayName = PlainTextComponentSerializer.plainText()
                            .serialize(ItemStack.of(m).effectiveName());
                    String key = m.key().toString();

                    nameToMat.put(displayName.toLowerCase(Locale.ROOT), m);
                    keyToMat.put(key.toLowerCase(Locale.ROOT), m);
                    matToName.put(m, displayName);
                    matToKey.put(m, key);
                });

        List<String> names = new ArrayList<>(nameToMat.keySet());
        Collections.sort(names);
        sortedNames = Collections.unmodifiableList(names);
    }

    public static void onDisable() {
        nameToMat.clear();
        keyToMat.clear();
        matToName.clear();
        matToKey.clear();
        sortedNames = Collections.emptyList();
    }

    public static void onSave() {

    }

    public static Material getById(String id) {
        if (id == null || id.isEmpty()) return null;
        String normalized = id.startsWith("minecraft:") ? id : "minecraft:" + id;
        return keyToMat.get(normalized.toLowerCase(Locale.ROOT));
    }

    public static Material getByName(String name) {
        if (name == null || name.isEmpty()) return null;
        return nameToMat.get(name.toLowerCase(Locale.ROOT));
    }

    public static String getDisplayName(Material material) {
        return matToName.getOrDefault(material, material.key().toString());
    }

    public static String getKey(Material material) {
        return matToKey.getOrDefault(material, material.key().toString());
    }

    public static Material getByNameClosest(String query) {
        if (query == null || query.isEmpty()) return null;
        String lower = query.toLowerCase(Locale.ROOT);

        int idx = Collections.binarySearch(sortedNames, lower);
        if (idx < 0) idx = -idx - 1;
        if (idx >= sortedNames.size()) return null;

        String candidate = sortedNames.get(idx);
        if (!candidate.startsWith(lower)) return null;

        return nameToMat.get(candidate);
    }

    public static List<Material> searchByName(String query) {
        if (query == null || query.isEmpty()) return Collections.emptyList();
        String lower = query.toLowerCase(Locale.ROOT);

        int idx = Collections.binarySearch(sortedNames, lower);
        if (idx < 0) idx = -idx - 1;

        List<Material> result = new ArrayList<>();
        for (int i = idx; i < sortedNames.size(); i++) {
            String name = sortedNames.get(i);
            if (!name.startsWith(lower)) break;
            result.add(nameToMat.get(name));
        }
        return result;
    }

    public static List<Material> getAll() {
        return new ArrayList<>(matToName.keySet());
    }
}