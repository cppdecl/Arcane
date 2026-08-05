package cx.arcane.utils;

import cx.arcane.Arcane;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Centralized logging utility for Arcane.
 *
 * Supports SLF4J-style "{}" placeholder formatting.
 *
 * Example:
 *     Log.info("Player {} joined with {} ms ping", name, ping);
 *
 * Color mapping:
 * - INFO  -> LIGHT_PURPLE
 * - WARN  -> GOLD
 * - ERROR -> RED
 *
 * Formatting behavior:
 * - Each "{}" is replaced sequentially.
 * - Extra arguments are ignored.
 * - Unmatched "{}" remain unchanged.
 * - null arguments are safely converted using String.valueOf().
 */
public final class Log {

    /**
     * Logs an informational message.
     *
     * @param message Message template containing "{}" placeholders.
     * @param args    Arguments used to replace placeholders.
     */
    public static void info(String message, Object... args) {
        Arcane.getPlugin().getComponentLogger().info(
                Component.text(format(message, args)).color(Colors.HOT_PINK)
        );
    }

    /**
     * Logs a warning message.
     *
     * @param message Message template containing "{}" placeholders.
     * @param args    Arguments used to replace placeholders.
     */
    public static void warn(String message, Object... args) {
        Arcane.getPlugin().getComponentLogger().warn(
                Component.text(format(message, args), NamedTextColor.GOLD)
        );
    }

    /**
     * Logs a warning message.
     *
     * @param message Message template containing "{}" placeholders.
     * @param args    Arguments used to replace placeholders.
     */
    public static void debug(String message, Object... args) {
        Arcane.getPlugin().getComponentLogger().debug(
                Component.text(format(message, args), NamedTextColor.GOLD)
        );
    }

    /**
     * Logs an error message.
     *
     * @param message Message template containing "{}" placeholders.
     * @param args    Arguments used to replace placeholders.
     */
    public static void error(String message, Object... args) {
        Arcane.getPlugin().getComponentLogger().error(
                Component.text(format(message, args), NamedTextColor.RED)
        );
    }

    /**
     * Replaces "{}" placeholders in a message with provided arguments.
     *
     * @param message The message template.
     * @param args    Arguments to inject.
     * @return Formatted message.
     */
    private static String format(String message, Object... args) {

        if (args == null || args.length == 0) {
            return message;
        }

        StringBuilder builder = new StringBuilder();
        int argIndex = 0;
        int cursor = 0;

        while (cursor < message.length()) {

            int placeholderIndex = message.indexOf("{}", cursor);

            if (placeholderIndex == -1 || argIndex >= args.length) {
                builder.append(message.substring(cursor));
                break;
            }

            builder.append(message, cursor, placeholderIndex);
            builder.append(String.valueOf(args[argIndex++]));
            cursor = placeholderIndex + 2;
        }

        return builder.toString();
    }

    private Log() {
        throw new UnsupportedOperationException("Utility Class - Cannot Instantiate!");
    }
}