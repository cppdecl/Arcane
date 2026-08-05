package cx.arcane.managers.commandManager.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import cx.arcane.utils.Colors;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ColorsCommand {

    static final int TOTAL_PAGES = 10;
    static final int COLS        = 40;
    static final int ROWS        = 9;

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("colors")
                .then(Commands.argument("page", IntegerArgumentType.integer(1, TOTAL_PAGES))
                        .executes(ctx -> runColors(ctx, IntegerArgumentType.getInteger(ctx, "page")))
                )
                .requires(c -> c.getSender().hasPermission("arcane.rank.management"))
                .executes(ctx -> runColors(ctx, TOTAL_PAGES))
                .build();
    }

    private static int runColors(CommandContext<CommandSourceStack> ctx, int page) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(buildPage(page));
        return Command.SINGLE_SUCCESS;
    }

    private static Component buildPage(int page) {
        float saturation = page / (float) TOTAL_PAGES;

        var builder = Component.text();

        for (int i = 0; i < 15; i++) {
            builder.append(Component.newline());
        }

        for (int row = 0; row < ROWS; row++) {
            if (row > 0) builder.append(Component.newline());

            float brightness = 0.3f + (row / (float)(ROWS - 1)) * 0.7f;

            for (int col = 0; col < COLS; col++) {
                float hue = col / (float) COLS;
                int rgb = hsvToRgb(hue, saturation, brightness);
                builder.append(swatch(rgb));
            }
        }

        builder.append(Component.newline());
        builder.append(navBar(page));

        return builder.build();
    }

    private static Component swatch(int rgb) {
        String hex = String.format("#%06X", rgb);
        TextColor color = TextColor.color(rgb);

        return Component.text("⬛")
                .color(color)
                .hoverEvent(HoverEvent.showText(
                        Component.text()
                                .append(Component.text(hex + " — ⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛").color(color))
                                .append(Component.newline())
                                .append(Component.text("ᴄʟɪᴄᴋ ᴛᴏ ᴄᴏᴘʏ ʜᴇx ᴛᴏ ᴄʟɪᴘʙᴏᴀʀᴅ").color(Colors.HOT_PINK))
                                .build()
                ))
                .clickEvent(ClickEvent.copyToClipboard(hex));
    }

    private static Component navBar(int page) {
        Component prev = page > 1
                ? Component.text("[ᴅᴇᴄʀᴇᴀsᴇ]").color(TextColor.color(Colors.HOT_PINK))
                .clickEvent(ClickEvent.runCommand("/colors " + (page - 1)))
                .hoverEvent(HoverEvent.showText(Component.text("Page " + (page - 1) + " — Saturation " + Math.round(((page - 1) / (float) TOTAL_PAGES) * 100) + "%", Colors.HOT_PINK)))
                : Component.text("[ᴅᴇᴄʀᴇᴀsᴇ]").color(Colors.DARK_PINK);

        Component next = page < TOTAL_PAGES
                ? Component.text(" [ɪɴᴄʀᴇᴀsᴇ]").color(TextColor.color(Colors.HOT_PINK))
                .clickEvent(ClickEvent.runCommand("/colors " + (page + 1)))
                .hoverEvent(HoverEvent.showText(Component.text("Page " + (page + 1) + " — Saturation " + Math.round(((page + 1) / (float) TOTAL_PAGES) * 100) + "%", Colors.HOT_PINK)))
                : Component.text(" [ɪɴᴄʀᴇᴀsᴇ]").color(Colors.DARK_PINK);

        Component label = Component.text("sᴀᴛᴜʀᴀᴛɪᴏɴ — ").color(Colors.HOT_PINK);

        return Component.text().append(label, prev, next).build();
    }

    private static int hsvToRgb(float h, float s, float v) {
        int i = (int)(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }
}