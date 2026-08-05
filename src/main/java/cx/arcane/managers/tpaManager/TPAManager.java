package cx.arcane.managers.tpaManager;

import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerSettings;
import cx.arcane.managers.teleportManager.TeleportManager;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TPAManager {

    private static final long EXPIRY_MILLIS = 60 * 1000; // 15 seconds

    public enum TeleportType {
        SENDER_TO_TARGET,
        TARGET_TO_SENDER
    }

    public static class TPARequest {
        private final UUID requestUUID = UUID.randomUUID();
        private final UUID sender;
        private final UUID target;
        private final TeleportType type;
        private final long timestamp;

        public TPARequest(UUID sender, UUID target, TeleportType type) {
            this.sender = sender;
            this.target = target;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }

        public UUID getRequestUUID() {
            return requestUUID;
        }

        public UUID getSender() {
            return sender;
        }

        public UUID getTarget() {
            return target;
        }

        public TeleportType getType() {
            return type;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) >= EXPIRY_MILLIS;
        }
    }

    private static final ConcurrentHashMap<UUID, Set<TPARequest>> outgoingRequests = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Set<TPARequest>> incomingRequests = new ConcurrentHashMap<>();

    private TPAManager() {
    }

    public static void onEnable() {

    }

    public static void onDisable() {

    }

    public static void onSave() {

    }

    public static void addRequest(Player sender, Player target, TeleportType type) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        cleanup(senderId);
        cleanup(targetId);

        TPARequest request = new TPARequest(senderId, targetId, type);

        outgoingRequests.computeIfAbsent(senderId, k -> ConcurrentHashMap.newKeySet()).add(request);
        incomingRequests.computeIfAbsent(targetId, k -> ConcurrentHashMap.newKeySet()).add(request);

        if (type == TeleportType.SENDER_TO_TARGET) {
            PlayerSettings pDestinationSettings = PlayerManager.getByUniqueId(targetId).getSettings();
            if (pDestinationSettings.isAutoAcceptTpaRequests()) {
                acceptIncoming(target, senderId);
                TeleportManager.teleport(sender, target).start();
            }
        }
        else {
            PlayerSettings pDestinationSettings = PlayerManager.getByUniqueId(targetId).getSettings();
            if (pDestinationSettings.isAutoAcceptTpaHereRequests()) {
                acceptIncoming(target, senderId);
                TeleportManager.teleport(target, sender).start();
            }
        }
    }

    public static TPARequest getRequest(Player sender, Player target) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        cleanup(senderId);
        cleanup(targetId);

        Set<TPARequest> outgoing = outgoingRequests.getOrDefault(senderId, Collections.emptySet());
        return outgoing.stream()
                .filter(req -> req.getTarget().equals(targetId) && !req.isExpired())
                .findFirst()
                .orElse(null);
    }

    public static boolean hasValidOutgoingRequest(Player sender, Player target) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        cleanup(senderId);
        cleanup(targetId);

        Set<TPARequest> outgoing = outgoingRequests.getOrDefault(senderId, Collections.emptySet());

        return outgoing.stream()
                .anyMatch(req -> req.getTarget().equals(targetId) && !req.isExpired());
    }

    public static boolean cancelOutgoing(Player sender, UUID targetId) {
        cleanup(sender.getUniqueId());
        cleanup(targetId);

        UUID senderId = sender.getUniqueId();
        Set<TPARequest> outgoing = outgoingRequests.getOrDefault(senderId, Collections.emptySet());

        Optional<TPARequest> toRemove = outgoing.stream()
                .filter(req -> req.getTarget().equals(targetId))
                .findFirst();

        if (toRemove.isPresent()) {
            removeRequest(toRemove.get());
            return true;
        }
        return false;
    }

    public static boolean acceptIncoming(Player target, UUID senderId) {
        cleanup(target.getUniqueId());
        cleanup(senderId);

        UUID targetId = target.getUniqueId();
        Set<TPARequest> incoming = incomingRequests.getOrDefault(targetId, Collections.emptySet());

        Optional<TPARequest> toAccept = incoming.stream()
                .filter(req -> req.getSender().equals(senderId))
                .findFirst();

        if (toAccept.isPresent() && !toAccept.get().isExpired()) {
            removeRequest(toAccept.get());
            return true;
        }
        return false;
    }

    public static boolean denyIncoming(Player target, UUID senderId) {
        cleanup(target.getUniqueId());
        cleanup(senderId);

        UUID targetId = target.getUniqueId();
        Set<TPARequest> incoming = incomingRequests.getOrDefault(targetId, Collections.emptySet());

        Optional<TPARequest> toDeny = incoming.stream()
                .filter(req -> req.getSender().equals(senderId))
                .findFirst();

        if (toDeny.isPresent()) {
            removeRequest(toDeny.get());
            return true;
        }
        return false;
    }

    private static void removeRequest(TPARequest request) {
        outgoingRequests.getOrDefault(request.getSender(), Collections.emptySet()).remove(request);
        incomingRequests.getOrDefault(request.getTarget(), Collections.emptySet()).remove(request);
    }

    private static void cleanup(UUID playerId) {
        outgoingRequests.computeIfPresent(playerId, (id, set) -> {
            set.removeIf(TPARequest::isExpired);
            return set.isEmpty() ? null : set;
        });

        incomingRequests.computeIfPresent(playerId, (id, set) -> {
            set.removeIf(TPARequest::isExpired);
            return set.isEmpty() ? null : set;
        });
    }

    public static Set<TPARequest> getOutgoing(Player sender) {
        cleanup(sender.getUniqueId());
        return Collections.unmodifiableSet(
                outgoingRequests.getOrDefault(sender.getUniqueId(), Collections.emptySet())
        );
    }

    public static Set<TPARequest> getIncoming(Player target) {
        cleanup(target.getUniqueId());
        return Collections.unmodifiableSet(
                incomingRequests.getOrDefault(target.getUniqueId(), Collections.emptySet())
        );
    }

    public static void clearAll(Player player) {
        UUID id = player.getUniqueId();

        Set<TPARequest> outgoing = outgoingRequests.remove(id);
        if (outgoing != null) {
            for (TPARequest req : outgoing) {
                incomingRequests.getOrDefault(req.getTarget(), Collections.emptySet()).remove(req);
            }
        }

        Set<TPARequest> incoming = incomingRequests.remove(id);
        if (incoming != null) {
            for (TPARequest req : incoming) {
                outgoingRequests.getOrDefault(req.getSender(), Collections.emptySet()).remove(req);
            }
        }
    }
}
