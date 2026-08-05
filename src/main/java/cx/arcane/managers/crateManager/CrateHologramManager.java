package cx.arcane.managers.crateManager;

import cx.arcane.utils.Text;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;

import java.util.Locale;

public class CrateHologramManager {

    private static final double SPACING = 0.3;
    private static final String PREFIX = "acxGeneratedCrates";

    private static String headerName(String crateId)      { return PREFIX + crateId + "Header"; }
    private static String descName(String crateId)        { return PREFIX + crateId + "Description"; }
    private static String keyCountName(String crateId)    { return PREFIX + crateId + "KeyCount"; }

    public static void createHolograms(CrateData crate) {
        Location base = crate.getLocation();
        if (base == null) return;

        runAtCrate(crate, () -> {
            Location headerLoc   = base.clone().add(0.5, 1.8, 0.5);
            Location descLoc     = headerLoc.clone().add(0, -SPACING, 0);
            Location keyCountLoc = descLoc.clone().add(0, -SPACING, 0);

            TextColor color = crate.getColor();
            String colorHex = "#" + String.format("%06X", color.value());

            String headerText    = "<" + colorHex + ">" + Text.toSmallCaps(crate.getId() + " Crate");
            String descText      = toMiniMessage(crate);
            String keyCountText  = "<" + colorHex + ">%arcane_crate_key_count_" + crate.getId().toLowerCase(Locale.ROOT) + "% <gray>Keys";

            createOrReplace(headerName(crate.getId()),   headerLoc,   headerText);
            createOrReplace(descName(crate.getId()),     descLoc,     descText);
            createOrReplace(keyCountName(crate.getId()), keyCountLoc, keyCountText);
        });
    }

    public static void removeHolograms(CrateData crate) {
        runAtCrate(crate, () -> {
            HologramManager mgr = FancyHologramsPlugin.get().getHologramManager();
            String crateId = crate.getId();
            mgr.getHologram(headerName(crateId)).ifPresent(mgr::removeHologram);
            mgr.getHologram(descName(crateId)).ifPresent(mgr::removeHologram);
            mgr.getHologram(keyCountName(crateId)).ifPresent(mgr::removeHologram);
        });
    }

    public static void ensureHolograms(CrateData crate) {
        runAtCrate(crate, () -> {
            HologramManager mgr = FancyHologramsPlugin.get().getHologramManager();
            boolean missing = mgr.getHologram(headerName(crate.getId())).isEmpty()
                    || mgr.getHologram(descName(crate.getId())).isEmpty()
                    || mgr.getHologram(keyCountName(crate.getId())).isEmpty();
            if (missing) createHolograms(crate);
        });
    }

    public static boolean isCrateHologram(String name) {
        return name.startsWith(PREFIX);
    }

    public static String crateIdFromHologramName(String name) {
        if (!isCrateHologram(name)) return null;
        String stripped = name.substring(PREFIX.length());
        for (String suffix : new String[]{"Header", "Description", "KeyCount"}) {
            if (stripped.endsWith(suffix)) return stripped.substring(0, stripped.length() - suffix.length());
        }
        return null;
    }

    private static void createOrReplace(String name, Location loc, String text) {
        HologramManager mgr = FancyHologramsPlugin.get().getHologramManager();
        mgr.getHologram(name).ifPresent(mgr::removeHologram);

        TextHologramData data = new TextHologramData(name, loc);
        data.setText(java.util.List.of(text));
        data.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        data.setBrightness(new Display.Brightness(15, 15));
        data.setTextShadow(true);
        data.setVisibilityDistance(128);
        data.setTextUpdateInterval(1);

        Hologram holo = mgr.create(data);
        holo.getData().setPersistent(true);
        mgr.addHologram(holo);
    }

    private static String toMiniMessage(CrateData crate) {
        if (crate.getDescription() == null) return "<white>No description.";
        String plain = LegacyComponentSerializer.legacySection().serialize(crate.getDescription());
        return plain.isEmpty() ? "<white>No description." : plain;
    }

    /**
     * Runs a task on the crate's region thread. No-op if location is null.
     */
    private static void runAtCrate(CrateData crate, Runnable task) {
        if (crate == null) return;
        Location loc = crate.getLocation();
        if (loc == null) return;

        Bukkit.getRegionScheduler().run(
                cx.arcane.Arcane.getPlugin(),
                loc,
                t -> {
                    task.run();
                }
        );
    }
}