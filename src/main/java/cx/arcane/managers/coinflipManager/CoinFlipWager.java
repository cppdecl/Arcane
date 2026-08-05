package cx.arcane.managers.coinflipManager;

import java.util.UUID;

public record CoinFlipWager(UUID wagerId, UUID ownerId, long amount) {
    public static CoinFlipWager of(UUID ownerId, long amount) {
        return new CoinFlipWager(UUID.randomUUID(), ownerId, amount);
    }
}