package cx.arcane.managers.msgManager;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@NullMarked
public final class MsgManager {

    private MsgManager() {}

    private static final Map<UUID, UUID> LAST_CONTACT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_MESSAGE_TIME = new ConcurrentHashMap<>();

    private static final long REPLY_TIMEOUT_MS = 3 * 60 * 1000;

    public static void recordMessage(Player from, Player to) {
        UUID fromId = from.getUniqueId();
        UUID toId = to.getUniqueId();

        long now = System.currentTimeMillis();

        LAST_CONTACT.put(fromId, toId);
        LAST_CONTACT.put(toId, fromId);

        LAST_MESSAGE_TIME.put(fromId, now);
        LAST_MESSAGE_TIME.put(toId, now);
    }

    public static @Nullable UUID getReplyTarget(UUID sender) {
        if (!isValid(sender)) {
            clear(sender);
            return null;
        }
        return LAST_CONTACT.get(sender);
    }

    public static long getLastMessageTime(UUID sender) {
        return LAST_MESSAGE_TIME.getOrDefault(sender, 0L);
    }

    public static boolean hasReplyTarget(UUID sender) {
        if (!isValid(sender)) {
            clear(sender);
            return false;
        }
        return LAST_CONTACT.containsKey(sender);
    }

    public static void clear(Player player) {
        clear(player.getUniqueId());
    }

    private static void clear(UUID uuid) {
        LAST_CONTACT.remove(uuid);
        LAST_MESSAGE_TIME.remove(uuid);
    }

    private static boolean isValid(UUID sender) {
        long last = LAST_MESSAGE_TIME.getOrDefault(sender, 0L);
        return last != 0L && System.currentTimeMillis() - last <= REPLY_TIMEOUT_MS;
    }
}
