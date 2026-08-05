package cx.arcane.managers.ordersManager;

public enum OrderPickerSortType {
    A_Z("A-Z"),
    Z_A("Z-A"),
    HIGHEST_PRICE("Highest Worth"),
    LOWEST_PRICE("Lowest Worth");

    private final String displayName;

    OrderPickerSortType(String displayName) {
        this.displayName = displayName;
    }

    public static OrderPickerSortType next(OrderPickerSortType current) {
        OrderPickerSortType[] values = values();
        return values[(current.ordinal() + 1) % values.length];
    }

    @Override
    public String toString() {
        return displayName;
    }
}