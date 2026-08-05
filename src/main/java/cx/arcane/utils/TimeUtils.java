package cx.arcane.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {


    private static final int TICKS_PER_SECOND = 20;
    private static final int MS_PER_TICK = 1000 / TICKS_PER_SECOND;

    public static long msToTicks(long ms) {
        return ms / MS_PER_TICK;
    }

    public static long ticksToMs(long ticks) {
        return ticks * MS_PER_TICK;
    }

    private static final Pattern TIME =
            Pattern.compile("(\\d+)(d|h|m|s|ms)");

    // returns ms
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

    public static String formatTimeRemainingFull(Instant start, long durationMs) {
        Instant expiry = start.plusMillis(durationMs);
        long remainingMs = Math.max(0, Duration.between(Instant.now(), expiry).toMillis());

        long days = remainingMs / (1000 * 60 * 60 * 24);
        long hours = (remainingMs / (1000 * 60 * 60)) % 24;
        long minutes = (remainingMs / (1000 * 60)) % 60;
        long seconds = (remainingMs / 1000) % 60;

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public static String formatTimeRemainingShort(Instant start, long durationMs) {
        long now = Instant.now().toEpochMilli();
        long end = start.toEpochMilli() + durationMs;

        long remainingMs = Math.max(0, end - now);
        long seconds = remainingMs / 1000;

        long days = seconds / 86400;
        seconds %= 86400;

        long hours = seconds / 3600;
        seconds %= 3600;

        long minutes = seconds / 60;
        seconds %= 60;

        // days + hours
        if (days > 0) {
            if (hours > 0) {
                return days + "d, " + hours + "h";
            }
            return days + "d";
        }

        // hours + minutes
        if (hours > 0) {
            if (minutes > 0) {
                return hours + "h and " + minutes + "m";
            }
            return hours + "h";
        }

        // minutes + seconds
        if (minutes > 0) {
            if (seconds > 0) {
                return minutes + "m and " + seconds + "s";
            }
            return minutes + "m";
        }

        // seconds only
        return Math.max(1, seconds) + "s";
    }

}
