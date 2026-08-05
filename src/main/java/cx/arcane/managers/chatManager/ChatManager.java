package cx.arcane.managers.chatManager;

import cx.arcane.Arcane;
import lombok.Getter;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ChatManager {

    public record ChatInfo(UUID senderId, String message, Instant sentAt) {}

    @Getter
    private static final ConcurrentHashMap<UUID, ChatInfo> lastSentMessageCache = new ConcurrentHashMap<>();

    @Getter
    private static final ConcurrentLinkedDeque<ChatInfo> messageHistory = new ConcurrentLinkedDeque<>();

    private static final ConcurrentHashMap<UUID, SpamState> SPAM_STATES = new ConcurrentHashMap<>();

    private static final int    BURST_FREE           = 5;
    private static final long   WINDOW_BASE_MS       = 60_000L;
    private static final long   WINDOW_MIN_MS        = 15_000L;
    private static final float  SIMILARITY_THRESHOLD = 0.70f;
    private static final int    HISTORY_SIZE         = 10;
    private static final long   SENSITIVITY_DECAY_MS = 5 * 60_000L;
    private static final int    MAX_FLAG_COUNT       = BURST_FREE - 1;

    static final class SpamState {
        final Deque<ChatInfo> recent = new ArrayDeque<>();
        int  flagCount    = 0;
        long lastFlagTime = 0L;

        int burstCount() {
            return recent.size();
        }

        long windowMs() {
            return Math.max(WINDOW_MIN_MS, WINDOW_BASE_MS - (flagCount * 10_000L));
        }

        int burstLimit() {
            return Math.max(2, BURST_FREE - flagCount);
        }

        void decayIfNeeded() {
            if (flagCount > 0 && lastFlagTime > 0
                    && System.currentTimeMillis() - lastFlagTime > SENSITIVITY_DECAY_MS) {
                flagCount = 0;
                lastFlagTime = 0L;
            }
        }

        void pruneOld(long windowMs) {
            long cutoff = System.currentTimeMillis() - windowMs;
            recent.removeIf(c -> c.sentAt().toEpochMilli() < cutoff);
        }
    }

    public static boolean isChatSpam(UUID playerId, String message) {
        SpamState state = SPAM_STATES.computeIfAbsent(playerId, k -> new SpamState());

        synchronized (state) {
            state.decayIfNeeded();
            state.pruneOld(state.windowMs());

            if (state.recent.isEmpty()) {
                state.recent.addLast(new ChatInfo(playerId, message, Instant.now()));
                return false;
            }

            float maxSimilarity = 0f;
            for (ChatInfo past : state.recent) {
                float sim = similarity(past.message(), message);
                if (sim > maxSimilarity) maxSimilarity = sim;
            }

            boolean similar = maxSimilarity >= SIMILARITY_THRESHOLD;

            if (!similar) {
                state.recent.addLast(new ChatInfo(playerId, message, Instant.now()));
                if (state.recent.size() > HISTORY_SIZE) state.recent.pollFirst();
                return false;
            }

            int burstLimit = state.burstLimit();
            long count = state.recent.stream()
                    .filter(c -> similarity(c.message(), message) >= SIMILARITY_THRESHOLD)
                    .count();

            if (count < burstLimit) {
                state.recent.addLast(new ChatInfo(playerId, message, Instant.now()));
                if (state.recent.size() > HISTORY_SIZE) state.recent.pollFirst();
                return false;
            }

            state.flagCount = Math.min(state.flagCount + 1, MAX_FLAG_COUNT);
            state.lastFlagTime = System.currentTimeMillis();
            return true;
        }
    }

    private static float similarity(String a, String b) {
        if (a.equals(b)) return 1f;
        a = a.toLowerCase();
        b = b.toLowerCase();
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1f;
        return 1f - ((float) levenshtein(a, b) / maxLen);
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                curr[j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? prev[j - 1]
                        : 1 + Math.min(prev[j - 1], Math.min(prev[j], curr[j - 1]));
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }

    public static void clearSpamState(UUID playerId) {
        SPAM_STATES.remove(playerId);
    }

    public static void onEnable() {
        Bukkit.getPluginManager().registerEvents(new ChatListener(), Arcane.getPlugin());
    }

    public static void onDisable() {}

    public static void onSave() {}
}