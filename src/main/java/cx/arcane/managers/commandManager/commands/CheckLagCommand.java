package cx.arcane.managers.commandManager.commands;

import cx.arcane.Arcane;
import cx.arcane.utils.Colors;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import io.papermc.paper.threadedregions.RegionizedServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.HopperMinecart;

import javax.annotation.Nullable;
import java.util.*;

@Command(name = "checklag", aliases = {"clag", "cl"})
@Permission("arcane.rank.management")
public class CheckLagCommand {

    private static final int PAGE_SIZE = 10;

    private record RegionInfo(
            World world, int cx, int cz,
            double tps, double mspt,
            int playerCount, int entityCount, int tileEntityCount
    ) {}

    // -------------------------------------------------------------------------
    // /cl [page]
    // -------------------------------------------------------------------------

    @Execute
    public void execute(@Sender Player sender) {
        showPage(sender, 1);
    }

    @Execute
    public void executePage(@Sender Player sender, @Arg int page) {
        showPage(sender, page);
    }

    private void showPage(Player sender, int page) {
        Bukkit.getAsyncScheduler().runNow(Arcane.getPlugin(), task -> {
            List<RegionInfo> regions = collectRegions();
            regions.sort(Comparator.comparingDouble(r -> -(r.tileEntityCount() + r.entityCount())));

            int totalPages = Math.max(1, (int) Math.ceil(regions.size() / (double) PAGE_SIZE));
            int clampedPage = Math.max(1, Math.min(page, totalPages));
            int from = (clampedPage - 1) * PAGE_SIZE;
            int to   = Math.min(from + PAGE_SIZE, regions.size());

            Component msg = header("Region Lag Index (Page " + clampedPage + "/" + totalPages + ")")
                    .append(Component.newline());

            if (regions.isEmpty()) {
                msg = msg.append(Component.text("  No regions found.", Colors.GRAY));
            } else {
                for (int i = from; i < to; i++) {
                    RegionInfo r = regions.get(i);
                    msg = msg.append(buildRegionLine(i + 1, r)).append(Component.newline());
                }
            }

            msg = msg.append(Component.newline()).append(buildPagination(clampedPage, totalPages));

            sender.sendMessage(msg);
        });
    }

    private Component buildRegionLine(int rank, RegionInfo r) {
        String coord = r.world().getName() + " (" + (r.cx() * 16) + ", " + (r.cz() * 16) + ")";
        String tpsStr  = String.format("%.1f", r.tps());
        String msptStr = String.format("%.1f", r.mspt());

        Component tpsColor  = Component.text(tpsStr + " TPS", tpsColor(r.tps()));
        Component msptColor = Component.text(msptStr + " MSPT", msptColor(r.mspt()));

        Component line = Component.text("#" + rank + " ", Colors.GRAY)
                .append(Component.text("Region at " + coord, Colors.HOT_PINK))
                .append(Component.text(" - ", Colors.GRAY))
                .append(tpsColor)
                .append(Component.text(" - ", Colors.GRAY))
                .append(msptColor);

        String detailCmd = "/cl " + (r.cx() * 16) + " " + (r.cz() * 16) + " " + r.world().getName();
        String hoverText = "Players: " + r.playerCount()
                + "\nEntities: " + r.entityCount()
                + "\nTile Entities: " + r.tileEntityCount()
                + "\nClick to view details / teleport";

        return line
                .clickEvent(ClickEvent.runCommand(detailCmd))
                .hoverEvent(HoverEvent.showText(Component.text(hoverText, Colors.GRAY)));
    }

    private Component buildPagination(int current, int total) {
        Component prev = current > 1
                ? Component.text("« Prev", Colors.HOT_PINK)
                        .clickEvent(ClickEvent.runCommand("/cl " + (current - 1)))
                        .hoverEvent(HoverEvent.showText(Component.text("Page " + (current - 1), Colors.GRAY)))
                : Component.text("« Prev", Colors.GRAY).decorate(TextDecoration.STRIKETHROUGH);

        Component next = current < total
                ? Component.text("Next »", Colors.HOT_PINK)
                        .clickEvent(ClickEvent.runCommand("/cl " + (current + 1)))
                        .hoverEvent(HoverEvent.showText(Component.text("Page " + (current + 1), Colors.GRAY)))
                : Component.text("Next »", Colors.GRAY).decorate(TextDecoration.STRIKETHROUGH);

        return prev
                .append(Component.text("  [" + current + "/" + total + "]  ", Colors.GRAY))
                .append(next);
    }

    // -------------------------------------------------------------------------
    // /cl <x> <z> [world]
    // -------------------------------------------------------------------------

    @Execute
    public void executeDetail(@Sender Player sender, @Arg int x, @Arg int z) {
        executeDetail(sender, x, z, sender.getWorld().getName());
    }

    @Execute
    public void executeDetail(@Sender Player sender, @Arg int x, @Arg int z, @Arg String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(Component.text("Unknown world: " + worldName, Colors.GRAY));
            return;
        }

        int cx = x >> 4;
        int cz = z >> 4;

        Bukkit.getAsyncScheduler().runNow(Arcane.getPlugin(), task -> {
            if (!world.isChunkLoaded(cx, cz)) {
                sender.sendMessage(Component.text("Chunk not loaded at " + x + ", " + z, Colors.GRAY));
                return;
            }

            Chunk chunk = world.getChunkAt(cx, cz);
            Map<String, Integer> players      = new LinkedHashMap<>();
            Map<String, Integer> droppedItems = new TreeMap<>();
            Map<String, Integer> tileEntities = new TreeMap<>();
            Map<String, Integer> redstone     = new TreeMap<>();
            Map<String, Integer> spawners     = new TreeMap<>();
            Map<String, Integer> other        = new TreeMap<>();

            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Player p) {
                    players.put(p.getName(), 1);
                } else if (entity instanceof Item item) {
                    String name = formatMaterial(item.getItemStack().getType());
                    droppedItems.merge(name, item.getItemStack().getAmount(), Integer::sum);
                }
            }

            for (BlockState state : chunk.getTileEntities()) {
                String name = formatMaterial(state.getType());
                if (isRedstone(state.getType())) {
                    redstone.merge(name, 1, Integer::sum);
                } else if (state.getType() == Material.SPAWNER) {
                    spawners.merge(name, 1, Integer::sum);
                } else {
                    tileEntities.merge(name, 1, Integer::sum);
                }
            }

            Component msg = header("Region Lag Information - " + world.getName() + " (" + x + ", " + z + ")")
                    .append(Component.newline())
                    .append(buildSection("Players",       players,      true))
                    .append(buildSection("Dropped Items", droppedItems, false))
                    .append(buildSection("Tile Entities", tileEntities, false))
                    .append(buildSection("Redstone",      redstone,     false))
                    .append(buildSection("Spawners",      spawners,     false));

            sender.sendMessage(msg);

            Player nearest = nearestPlayer(world, cx * 16 + 8, cz * 16 + 8);
            if (nearest != null) {
                Location tpLoc = nearest.getLocation();
                sender.sendMessage(
                        Component.text("  ► Teleport to region center", Colors.HOT_PINK)
                                .clickEvent(ClickEvent.runCommand("/tp " + sender.getName() + " " + (cx * 16 + 8) + " " + tpLoc.getBlockY() + " " + (cz * 16 + 8)))
                                .hoverEvent(HoverEvent.showText(Component.text("Nearest player: " + nearest.getName(), Colors.GRAY)))
                );
            }
        });
    }

    // -------------------------------------------------------------------------
    // Region collection (Folia)
    // -------------------------------------------------------------------------

    private List<RegionInfo> collectRegions() {
        List<RegionInfo> list = new ArrayList<>();

        /*for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                double tps  = 20.0;
                double mspt = 0.0;

                try {
                    RegionStats stats = RegionizedServer.getInstance().getRegionStats(world, chunk.getX(), chunk.getZ());
                    if (stats != null) {
                        tps  = stats.tps();
                        mspt = stats.mspt();
                    }
                } catch (Throwable ignored) {}

                Entity[] entities = chunk.getEntities();
                BlockState[] tiles = chunk.getTileEntities();
                int playerCount = 0;
                for (Entity e : entities) if (e instanceof Player) playerCount++;

                list.add(new RegionInfo(
                        world, chunk.getX(), chunk.getZ(),
                        tps, mspt,
                        playerCount, entities.length, tiles.length
                ));
            }
        }*/

        return list;
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    private Component header(String title) {
        return Component.text("━━━ ", Colors.GRAY)
                .append(Component.text(title, Colors.HOT_PINK).decorate(TextDecoration.BOLD))
                .append(Component.text(" ━━━", Colors.GRAY));
    }

    private Component buildSection(String title, Map<String, Integer> entries, boolean nameOnly) {
        if (entries.isEmpty()) return Component.empty();

        Component section = Component.text(title + ":", Colors.HOT_PINK)
                .decorate(TextDecoration.BOLD)
                .append(Component.newline());

        for (Map.Entry<String, Integer> entry : entries.entrySet()) {
            String value = nameOnly ? "" : ": " + formatCount(entry.getValue());
            section = section
                    .append(Component.text("  * ", Colors.GRAY))
                    .append(Component.text(entry.getKey() + value, Colors.GRAY))
                    .append(Component.newline());
        }

        return section;
    }

    private net.kyori.adventure.text.format.TextColor tpsColor(double tps) {
        if (tps >= 18) return net.kyori.adventure.text.format.TextColor.color(0x55FF55);
        if (tps >= 12) return net.kyori.adventure.text.format.TextColor.color(0xFFAA00);
        return net.kyori.adventure.text.format.TextColor.color(0xFF5555);
    }

    private net.kyori.adventure.text.format.TextColor msptColor(double mspt) {
        if (mspt <= 25)  return net.kyori.adventure.text.format.TextColor.color(0x55FF55);
        if (mspt <= 40)  return net.kyori.adventure.text.format.TextColor.color(0xFFAA00);
        return net.kyori.adventure.text.format.TextColor.color(0xFF5555);
    }

    private String formatCount(int count) {
        if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0);
        if (count >= 1_000)     return String.format("%.1fK", count / 1_000.0);
        return String.valueOf(count);
    }

    private String formatMaterial(Material mat) {
        String raw = mat.name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty())
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
        }
        return sb.toString().trim();
    }

    private boolean isRedstone(Material mat) {
        return switch (mat) {
            case REDSTONE_WIRE, REPEATER, COMPARATOR, OBSERVER,
                 PISTON, STICKY_PISTON, DISPENSER, DROPPER,
                 HOPPER, LEVER, TRIPWIRE_HOOK -> true;
            default -> false;
        };
    }

    @Nullable
    private Player nearestPlayer(World world, int x, int z) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player p : world.getPlayers()) {
            double d = p.getLocation().distanceSquared(new Location(world, x, p.getLocation().getY(), z));
            if (d < best) { best = d; nearest = p; }
        }
        return nearest;
    }
}