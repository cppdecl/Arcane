package cx.arcane.managers.ordersManager;

public enum OrderListingSortType {
    MOST_PAID("Most Paid"),
    LEAST_PAID("Least Paid"),
    MOST_DELIVERED("Most Delivered"),
    LEAST_DELIVERED("Least Delivered"),
    MOST_MONEY_PER_ITEM("Most Money Per Item"),
    LEAST_MONEY_PER_ITEM("Least Money Per Item"),
    OLDEST_LISTED("Oldest Listed"),
    RECENTLY_LISTED("Recently Listed");

    private final String displayName;

    OrderListingSortType(String displayName) {
        this.displayName = displayName;
    }

    public OrderListingSortType next() {
        OrderListingSortType[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    @Override
    public String toString() { return displayName; }
}