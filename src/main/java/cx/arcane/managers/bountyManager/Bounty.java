package cx.arcane.managers.bountyManager;

import java.util.UUID;

public class Bounty {
    private UUID uniqueId;
    private long reward;

    public Bounty(UUID listingId, long reward) {
        this.uniqueId = listingId;
        this.reward = reward;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public long getReward() {
        return reward;
    }
    public void setReward(long reward) {
        this.reward = reward;
    }
    public void increaseReward(long amount) {
        this.reward += amount;
    }
}
