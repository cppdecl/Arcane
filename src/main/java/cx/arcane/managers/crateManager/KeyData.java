package cx.arcane.managers.crateManager;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KeyData {

    @JsonProperty("playerId")
    private UUID playerId;

    @JsonProperty("keys")
    private Map<String, Long> keys;

    public KeyData() {}

    public KeyData(UUID playerId, Map<String, Long> keys) {
        this.playerId = playerId;
        this.keys = keys;
    }

    public UUID getPlayerId() { return playerId; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }

    public Map<String, Long> getKeys() { return keys; }
    public void setKeys(Map<String, Long> keys) { this.keys = keys; }

    @JsonIgnore public void clearKeys() { this.keys.clear(); }
    @JsonIgnore public void removeKey(String crateId) { this.keys.remove(crateId); }

    @JsonIgnore
    public void addKey(String crateId, long amount) {
        this.keys.merge(crateId, amount, Long::sum);
    }

    @JsonIgnore
    public void takeKey(String crateId, long amount) {
        long current = this.keys.getOrDefault(crateId, 0L) - amount;
        if (current <= 0) this.keys.remove(crateId);
        else this.keys.put(crateId, current);
    }

    @JsonIgnore public long getKeyCount(String crateId) { return this.keys.getOrDefault(crateId, 0L); }
    @JsonIgnore public List<String> getOwnedCrates() { return this.keys.keySet().stream().toList(); }
    @JsonIgnore public boolean hasKey(String crateId) { return this.keys.getOrDefault(crateId, 0L) > 0; }
}