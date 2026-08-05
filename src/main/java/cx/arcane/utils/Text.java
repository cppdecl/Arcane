package cx.arcane.utils;

import dev.triumphteam.gui.GuiView;
import dev.triumphteam.gui.container.GuiContainer;
import dev.triumphteam.gui.element.GuiItem;
import dev.triumphteam.gui.layout.GuiLayout;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import dev.triumphteam.gui.slot.Slot;
import dev.triumphteam.gui.state.pagination.PagerState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Text {

    private static final Pattern TIME =
            Pattern.compile("(\\d+)(d|h|m|s|ms)");


    public static long parseDurationString(String input) {
        Matcher m = TIME.matcher(input.toLowerCase());
        long total = 0;

        while (m.find()) {
            long value = Long.parseLong(m.group(1));
            switch (m.group(2)) {
                case "d" -> total += value * 86400000L;
                case "h" -> total += value * 3600000L;
                case "m" -> total += value * 60000L;
                case "s" -> total += value * 1000L;
                case "ms" -> total += value;
            }
        }
        return total;
    }

    public static String repeat(String s, int length) {
        return s.repeat(Math.max(0, length / s.length()));
    }

    public static String center(String text, int width) {
        if (text.length() >= width) {
            return text;
        }

        int padding = width - text.length();
        int left = padding / 2;
        int right = padding - left;

        return " ".repeat(left) + text + " ".repeat(right);
    }

    // replaces all "." with "{}" for use in logger formatting
    public static String formatLib(String dependency) {
        return dependency.replace(".", "{}");
    }

    public static String newlinedString(String... lines) {
        return String.join("\n", lines);
    }

    public static String newlinedString(List<String> lines) {
        return String.join("\n", lines);
    }

    public static String format(String template, Object... args) {
        for (Object arg : args) {
            template = template.replaceFirst("\\{}", String.valueOf(arg));
        }
        return template;
    }

    public static String safe(String str) {
        return str == null ? "N/A" : str;
    }

    private static final Map<Character, Character> SMALL_CAPS_MAP = new HashMap<>();
    private static final Map<Character, Character> REVERSE_SMALL_CAPS_MAP = new HashMap<>();

    static {
        SMALL_CAPS_MAP.put('a', '\u1D00');
        SMALL_CAPS_MAP.put('b', '\u0299');
        SMALL_CAPS_MAP.put('c', '\u1D04');
        SMALL_CAPS_MAP.put('d', '\u1D05');
        SMALL_CAPS_MAP.put('e', '\u1D07');
        SMALL_CAPS_MAP.put('f', '\uA730');
        SMALL_CAPS_MAP.put('g', '\u0262');
        SMALL_CAPS_MAP.put('h', '\u029C');
        SMALL_CAPS_MAP.put('i', '\u026A');
        SMALL_CAPS_MAP.put('j', '\u1D0A');
        SMALL_CAPS_MAP.put('k', '\u1D0B');
        SMALL_CAPS_MAP.put('l', '\u029F');
        SMALL_CAPS_MAP.put('m', '\u1D0D');
        SMALL_CAPS_MAP.put('n', '\u0274');
        SMALL_CAPS_MAP.put('o', '\u1D0F');
        SMALL_CAPS_MAP.put('p', '\u1D18');
        SMALL_CAPS_MAP.put('q', '\uA7AF');
        SMALL_CAPS_MAP.put('r', '\u0280');
        SMALL_CAPS_MAP.put('s', '\uA731');
        SMALL_CAPS_MAP.put('t', '\u1D1B');
        SMALL_CAPS_MAP.put('u', '\u1D1C');
        SMALL_CAPS_MAP.put('v', '\u1D20');
        SMALL_CAPS_MAP.put('w', '\u1D21');
        SMALL_CAPS_MAP.put('x', 'x');
        SMALL_CAPS_MAP.put('y', '\u028F');
        SMALL_CAPS_MAP.put('z', '\u1D22');

        SMALL_CAPS_MAP.put('0', '0');
        SMALL_CAPS_MAP.put('1', '1');
        SMALL_CAPS_MAP.put('2', '2');
        SMALL_CAPS_MAP.put('3', '3');
        SMALL_CAPS_MAP.put('4', '4');
        SMALL_CAPS_MAP.put('5', '5');
        SMALL_CAPS_MAP.put('6', '6');
        SMALL_CAPS_MAP.put('7', '7');
        SMALL_CAPS_MAP.put('8', '8');
        SMALL_CAPS_MAP.put('9', '9');

        SMALL_CAPS_MAP.put('/', '∕');
        SMALL_CAPS_MAP.put('-', '-');
        SMALL_CAPS_MAP.put('+', '+');
        SMALL_CAPS_MAP.put('=', '\u207C');
        SMALL_CAPS_MAP.put('(', '\u207D');
        SMALL_CAPS_MAP.put(')', '\u207E');
        SMALL_CAPS_MAP.put(':', '\u02D0');
        SMALL_CAPS_MAP.put('.', '․');
        SMALL_CAPS_MAP.put(',', '\u02CF');
        SMALL_CAPS_MAP.put('?', '\u02D9');
        SMALL_CAPS_MAP.put('!', '\u02D9');

        for (Map.Entry<Character, Character> e : SMALL_CAPS_MAP.entrySet()) {
            REVERSE_SMALL_CAPS_MAP.put(e.getValue(), e.getKey());
        }
    }

    public static String toSmallCaps(String input) {
        if (input == null) return null;
        return input.toLowerCase(Locale.ROOT).chars()
                .mapToObj(c -> String.valueOf(SMALL_CAPS_MAP.getOrDefault((char) c, (char) c)))
                .collect(Collectors.joining());
    }

    public static TextComponent toSmallCapsComponent(String input) {
        return Component.text(toSmallCaps(input)).decoration(TextDecoration.ITALIC, false);
    }

    public static String formatPunishmentDurationA(Long expiresAt) {
        if (expiresAt == null) {
            return "Permanent";
        }


        if (expiresAt == null) {
            return "Permanent";
        }

        long seconds = Duration.between(Instant.now(), Instant.ofEpochMilli(expiresAt)).getSeconds();
        if (seconds <= 0) {
            return "Expired";
        }

        long years = seconds / 31_536_000; // 365d
        seconds %= 31_536_000;

        long days = seconds / 86_400;
        seconds %= 86_400;

        long hours = seconds / 3_600;
        seconds %= 3_600;

        long minutes = seconds / 60;

        StringBuilder sb = new StringBuilder();

        if (years > 0) sb.append(years).append(years == 1 ? " Year" : " Years");
        if (days > 0) appendPart(sb, days, "Day");
        if (hours > 0 && sb.length() == 0) appendPart(sb, hours, "Hour");
        if (minutes > 0 && sb.length() == 0) appendPart(sb, minutes, "Minute");

        return sb.toString();
    }

    private static void appendPart(StringBuilder sb, long value, String unit) {
        if (sb.length() > 0) sb.append(" and ");
        sb.append(value).append(" ").append(unit);
    }


    public static String formatPunishCountdown(Long expiresAt) {
        if (expiresAt == null) {
            return "Permanent";
        }

        if (expiresAt == null) return "Permanent";

        long seconds = Duration.between(Instant.now(), Instant.ofEpochMilli(expiresAt)).getSeconds();
        if (seconds <= 0) return "Expired";

        long years = seconds / 31_536_000; // 365d
        seconds %= 31_536_000;

        long days = seconds / 86_400;
        seconds %= 86_400;

        long hours = seconds / 3_600;
        seconds %= 3_600;

        long minutes = seconds / 60;
        seconds %= 60;

        List<String> parts = new ArrayList<>();

        if (years > 0) parts.add(years + (years == 1 ? " Year" : " Years"));
        if (days > 0) parts.add(days + (days == 1 ? " Day" : " Days"));
        if (hours > 0) parts.add(hours + (hours == 1 ? " Hour" : " Hours"));
        if (minutes > 0) parts.add(minutes + (minutes == 1 ? " Minute" : " Minutes"));
        if (seconds > 0) parts.add(seconds + (seconds == 1 ? " Second" : " Seconds"));

        // Show only the first two largest units for readability
        if (parts.size() > 2) parts = parts.subList(0, 2);

        return String.join(", ", parts);
    }

    public static String formatCountdown(Long epochMillis) {
        if (epochMillis == null) {
            return "Permanent";
        }

        long seconds = Duration.between(Instant.now(), Instant.ofEpochMilli(epochMillis)).getSeconds();

        if (seconds <= 0) {
            return "Expired";
        }

        long days = seconds / 86_400;
        seconds %= 86_400;

        long hours = seconds / 3_600;
        seconds %= 3_600;

        long minutes = seconds / 60;
        seconds %= 60;

        long secs = seconds;

        if (days > 0) {
            return format(days, "Day", hours, "Hour");
        }
        if (hours > 0) {
            return format(hours, "Hour", minutes, "Minute");
        }
        if (minutes > 0) {
            return format(minutes, "Minute", secs, "Second");
        }
        return format(secs, "Second", 0, null);
    }

    private static String format(long primary, String primaryLabel,
                                 long secondary, String secondaryLabel) {

        StringBuilder sb = new StringBuilder();
        sb.append(primary).append(" ").append(primaryLabel);
        if (primary != 1) sb.append("s");

        if (secondary > 0 && secondaryLabel != null) {
            sb.append(", ")
                    .append(secondary).append(" ").append(secondaryLabel);
            if (secondary != 1) sb.append("s");
        }

        return sb.toString();
    }

    public static List<String> getAllZoneRegions() {
        return ZoneId.getAvailableZoneIds()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }

    public static String regionToGmtOffset(String regionId) {
        ZoneId zone = ZoneId.of(regionId);

        ZoneOffset offset = ZonedDateTime.now(zone).getOffset();

        int totalSeconds = offset.getTotalSeconds();
        int hours = totalSeconds / 3600;

        return "GMT" + (hours >= 0 ? "+" : "") + hours;
    }

    public static String instantToTimestamp(Instant instant) {
        return instantToTimestamp(instant, "GMT+8");
    }

    public static String instantToTimestamp(Instant instant, String timezone) {
        if (instant == null) return "N/A";

        ZonedDateTime zdt = instant.atZone(ZoneId.of(timezone));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                "MMMM d, yyyy 'at' h:mm a",
                Locale.ENGLISH
        );

        return zdt.format(formatter);
    }

    private static final Pattern PURE_HEX_PREFIX =
            Pattern.compile("(?<![<:#])#([A-Fa-f0-9]{6})(?![A-Fa-f0-9])");

    private static final Pattern LEGACY_HEX =
            Pattern.compile("(?<![<:])&#([A-Fa-f0-9]{6})");

    private static final Pattern LEGACY_CODE =
            Pattern.compile("&([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);

    private static final Map<Character, String> LEGACY_TO_MINI = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),

            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private static String legacyToMini(String input) {

        // 1️⃣ Legacy hex &#RRGGBB (outside tags only)
        input = LEGACY_HEX.matcher(input)
                .replaceAll("<#$1>");

        // 2️⃣ Pure hex #RRGGBB (outside tags only)
        input = PURE_HEX_PREFIX.matcher(input)
                .replaceAll("<#$1>");

        // 3️⃣ Legacy formatting codes
        Matcher matcher = LEGACY_CODE.matcher(input);
        StringBuffer out = new StringBuffer();

        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String replacement = LEGACY_TO_MINI.getOrDefault(code, "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);

        return out.toString();
    }


    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /**
     * Convert a Component to plain text string
     */
    public static String componentToString(Component component) {
        return MINI.serialize(component);
    }

    public static Component stringToComponent(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        input = input
                .replace("\\n", "<newline>")
                .replace("\n", "<newline>");

        String normalized = legacyToMini(input);
        return MINI.deserialize(normalized);
    }

    public static String formatRelativeTimestamp(Long epochMillis) {
        if (epochMillis == null) return "N/A";

        long now = System.currentTimeMillis();
        long diffMillis = epochMillis - now;
        boolean future = diffMillis > 0;

        long seconds = Math.abs(diffMillis) / 1000;
        if (seconds == 0) return "1 Second Ago";

        long years = seconds / 31_536_000; // 365d
        seconds %= 31_536_000;

        long months = seconds / 2_592_000; // 30d
        seconds %= 2_592_000;

        long days = seconds / 86_400;
        seconds %= 86_400;

        long hours = seconds / 3_600;
        seconds %= 3_600;

        long minutes = seconds / 60;
        seconds %= 60;

        List<String> parts = new ArrayList<>();

        addTimePart(parts, years, "Year");
        addTimePart(parts, months, "Month");
        addTimePart(parts, days, "Day");
        addTimePart(parts, hours, "Hour");
        addTimePart(parts, minutes, "Minute");
        addTimePart(parts, seconds, "Second");

        if (parts.isEmpty()) {
            parts.add("1 Second");
        }

        // max 2 units
        if (parts.size() > 2) {
            parts = parts.subList(0, 2);
        }

        String result = String.join(", ", parts);

        return future ? "In " + result : result + " Ago";
    }

    private static void addTimePart(List<String> parts, long value, String label) {
        if (value <= 0) return;

        parts.add(value + " " + label + (value == 1 ? "" : "s"));
    }

   public static Material worldToIcon(World world) {
       switch(world.getName()) {
           case "world" -> { return Material.GRASS_BLOCK; }
           case "world_nether" -> { return Material.NETHERRACK; }
           case "world_the_end" -> { return Material.END_STONE; }
           default -> { return Material.COMPASS; }
       }
   }

    public static String worldToName(World world) {
        switch(world.getName()) {
            case "world" -> { return "Overworld"; }
            case "world_nether" -> { return "Nether"; }
            case "world_the_end" -> { return "The End"; }
            default -> { return world.getName(); }
        }
    }

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###.##");
    private static final DecimalFormat INTEGER_FORMAT = new DecimalFormat("#,###");

    public static boolean isValidAmount(String amountArg) {

        long number;
        try {
            number = Text.parseAmountString(amountArg);
        } catch (Exception e) {
            return false;
        }

        final long MAX_AMOUNT = 1_000_000_000_000L;
        return number > 0 && number <= MAX_AMOUNT;
    }

    public static boolean isValidAmount(long number) {
        final long MAX_AMOUNT = 1_000_000_000_000L;
        return number > 0 && number <= MAX_AMOUNT;
    }

    public static String formatBalance(long number) {
        return INTEGER_FORMAT.format(number);
    }

    public static String formatShortBalanceWithSign(String sign, long amount) {
        return sign + formatShortBalance(amount);
    }

    public static String formatShortBalance(long amount) {
        if (amount == 0) return "0";

        String[] suffixes = {"", "K", "M", "B", "T", "Q"};
        int idx = 0;
        double value = amount;

        while (value >= 1000 && idx < suffixes.length - 1) {
            value /= 1000.0;
            idx++;
        }

        // Rounding check: If rounding to 2 decimals makes it 1000, move to next suffix
        if (Math.round(value * 100.0) / 100.0 >= 1000 && idx < suffixes.length - 1) {
            value = 1.0;
            idx++;
        }

        // Clean formatting
        if (value == Math.floor(value)) {
            return (long) value + suffixes[idx];
        }

        String formatted = String.format("%.2f", value);

        // Remove trailing zeros for a cleaner look (e.g., 999.90 -> 999.9)
        if (formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".0")) {
            formatted = formatted.substring(0, formatted.length() - 2);
        }

        return formatted + suffixes[idx];
    }

    public static long parseAmountString(String input) {
        if (input == null || input.isBlank()) return 0L;

        String cleaned = input.replace(",", "").trim().toUpperCase();
        long multiplier = 1L;

        char lastChar = cleaned.charAt(cleaned.length() - 1);
        switch (lastChar) {
            case 'K' -> multiplier = 1_000L;
            case 'M' -> multiplier = 1_000_000L;
            case 'B' -> multiplier = 1_000_000_000L;
            case 'T' -> multiplier = 1_000_000_000_000L;
            case 'Q' -> multiplier = 1_000_000_000_000_000L;
        }

        if (multiplier > 1) cleaned = cleaned.substring(0, cleaned.length() - 1);

        try {
            double value = Double.parseDouble(cleaned);
            return (long) (value * multiplier);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static String numberToNumerals(int number) {
        if (number < 0 || number > 3999) throw new IllegalArgumentException("Number must be between 0 and 3999");
        String[] M = {"", "M", "MM", "MMM"};
        String[] C = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] X = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] I = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return M[number / 1000] + C[(number % 1000) / 100] + X[(number % 100) / 10] + I[number % 10];
    }

    public static String removeNumerals(String input) {
        if (input == null) return null;
        return input.replaceAll("M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})", "");
    }


    public static Component removeItalicsFromComponent(Component component) {
        return component
                .decoration(TextDecoration.ITALIC, false)
                .children(component.children().stream()
                        .map(Text::removeItalicsFromComponent)
                        .toList());
    }

    public static List<Component> removeItalicsFromComponent(List<Component> components) {
        return components.stream()
                .map(Text::removeItalicsFromComponent)
                .toList();
    }

    public static Component getHandComponent(Player player) {
        var item = player.getInventory().getItemInMainHand();

        if (item.getType().isAir()) {
            return Component.text("Fists", Colors.RED);
        }

        int amount = item.getAmount();

        TextColor itemColor = null;
        Component itemComponent = Component.text(
                PlainTextComponentSerializer.plainText().serialize(item.effectiveName()),
                Colors.RED
        );

        if (item.getItemMeta().hasCustomName()) {
            itemComponent = item.effectiveName();
        }

        if (item.getItemMeta() != null && !item.getItemMeta().hasDisplayName()) {
            itemColor = Colors.RED;
        }

        if (item.getType() == Material.AIR) {
            itemComponent = Component.text("Fists");
        }

        itemComponent = itemComponent.hoverEvent(HoverEvent.showText(Component.text("Click to Inspect Weapon", Colors.HOT_PINK)))
                .clickEvent(ClickEvent.callback(a -> {
                    Player viewer = (Player) a;

                    Gui gui = Gui.of(3)
                            .title(Component.text(player.getName() + "'s Weapon"))
                            .statelessComponent(con -> {
                                ItemStack currentStack = item;
                                con.setItem(2, 5, ItemBuilder.from(currentStack).asGuiItem((p, ctx) -> {
                                    if (currentStack.getType().name().contains("SHULKER_BOX") || currentStack.getType().name().contains("BUNDLE")) {
                                        openShulkerOrBundle(viewer, currentStack, ctx.guiView());
                                    }
                                }));
                            })
                            .build();

                    gui.open(viewer);
                    viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()));

        return itemComponent;
    }

    public static void setInspectedItem(GuiContainer<Player, ItemStack> con, int row, int col, Player viewer, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return;

        con.setItem(row, col, ItemBuilder.from(stack).asGuiItem((p, ctx) -> {
            if (stack.getType().name().contains("SHULKER_BOX") || stack.getType().name().contains("BUNDLE")) {
                openShulkerOrBundle(viewer, stack, ctx.guiView());
            }
        }));
    }

    public static void openShulkerOrBundle(Player viewer, ItemStack item, GuiView parentGui) {
        if (item == null || item.getType().isAir()) return;

        // Check if it's a Shulker Box or a Bundle
        boolean isShulker = item.getType().name().contains("SHULKER_BOX");
        boolean isBundle = item.getType().name().contains("BUNDLE");

        if (!isShulker && !isBundle) return;

        // Extract contents into a list (filtering out air/null)
        List<ItemStack> contents = new ArrayList<>();
        if (isShulker) {
            BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
            ShulkerBox box = (ShulkerBox) meta.getBlockState();
            for (ItemStack i : box.getInventory().getContents()) {
                if (i != null && !i.getType().isAir()) contents.add(i);
            }
        } else {
            BundleMeta meta = (BundleMeta) item.getItemMeta();
            for (ItemStack i : meta.getItems()) {
                if (i != null && !i.getType().isAir()) contents.add(i);
            }
        }

        // STATE FLAG: Track if we are programmatically changing menus
        final var navigating = new boolean[]{false};

        // Pagination configuration
        int maxContentRows = 3;
        int itemsPerPage = maxContentRows * 7;
        boolean paginated = contents.size() > itemsPerPage;

        int contentRows = paginated
                ? maxContentRows
                : Math.max(1, (int) Math.ceil(contents.size() / 7.0));

        int rows = contentRows + 2;
        int totalPages = (contents.size() + itemsPerPage - 1) / itemsPerPage;

        Gui subGui = Gui.of(rows)
                .title(item.effectiveName().color(NamedTextColor.DARK_GRAY))
                .onClose(() -> {
                    // Only return to parent if the user closed the inventory (like pressing ESC)
                    if (!navigating[0]) {
                        parentGui.open();
                        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                    }
                })
                .component(component -> {

                    PagerState<ItemStack> pageState = null;

                    if (paginated) {
                        pageState = PagerState.of(
                                contents,
                                GuiLayout.box(Slot.of(2, 2), Slot.of(rows - 1, 8))
                        );
                        component.remember(pageState);
                    }

                    PagerState<ItemStack> finalPageState = pageState;

                    component.render(con -> {

                        if (paginated) {
                            // Paginated Render
                            finalPageState.forEach(entry -> {
                                ItemStack currentStack = entry.element();
                                // Pass the state flag to the items
                                con.setItem(entry.slot(), createContainerItem(viewer, currentStack, navigating));
                            });

                            if (finalPageState.getCurrentPage() > 1) {
                                con.setItem(rows, 1, prevItemButton(viewer, finalPageState));
                            }

                            if (finalPageState.getCurrentPage() < totalPages && totalPages > 1) {
                                con.setItem(rows, 9, nextItemButton(viewer, finalPageState));
                            }

                        } else {
                            // Regular non-paginated Render
                            int index = 0;
                            for (ItemStack currentStack : contents) {
                                int col = (index % 7) + 2;
                                int row = (index / 7) + 2;

                                // Pass the state flag to the items
                                con.setItem(row, col, createContainerItem(viewer, currentStack, navigating));
                                index++;
                            }
                        }

                        // Visible "Return" button
                        con.setItem(rows, 5, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                                .name(Text.toSmallCapsComponent("Back")
                                        .color(TextColor.color(0xff0000))
                                        .decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to return", Colors.WHITE)
                                        .decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true; // Mark as navigating so onClose doesn't trigger
                                    parentGui.open();
                                    viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                                }));
                    });
                })
                .build();

        subGui.open(viewer);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
    }

    public static GuiItem<Player, ItemStack> createContainerItem(Player viewer, ItemStack stack, boolean[] navigating) {
        return ItemBuilder.from(stack).asGuiItem((p, ctx) -> {
            if (stack.getType().name().contains("SHULKER_BOX") || stack.getType().name().contains("BUNDLE")) {
                navigating[0] = true; // Mark as navigating so onClose doesn't trigger
                openShulkerOrBundle(viewer, stack, ctx.guiView());
            }
        });
    }

    public static GuiItem<Player, ItemStack> prevItemButton(Player p, PagerState<ItemStack> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Previous")
                        .color(Colors.HOT_PINK)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to go to previous page", Colors.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1, 1);
                    state.prev();
                });
    }

    public static GuiItem<Player, ItemStack> nextItemButton(Player p, PagerState<ItemStack> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Next")
                        .color(Colors.HOT_PINK)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to go to next page", Colors.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1, 1);
                    state.next();
                });
    }
}
