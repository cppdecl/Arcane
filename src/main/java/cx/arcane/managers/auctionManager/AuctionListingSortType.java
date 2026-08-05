package cx.arcane.managers.auctionManager;

public enum AuctionListingSortType {
    HIGHEST_PRICE("Highest Price"),
    LOWEST_PRICE("Lowest Price"),
    OLDEST_LISTED("Oldest Listed"),
    RECENTLY_LISTED("Recently Listed");

    private final String displayName;

    AuctionListingSortType(String displayName) {
        this.displayName = displayName;
    }

    public AuctionListingSortType next() {
        AuctionListingSortType[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    @Override
    public String toString() { return displayName; }
}