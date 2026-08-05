package cx.arcane.utils;

public enum ItemPickerSortType {
    A_Z("A-Z"),
    Z_A("Z-A"),
    HIGHEST_PRICE("Highest Worth"),
    LOWEST_PRICE("Lowest Worth");

    private final String displayName;

    ItemPickerSortType(String displayName) {
        this.displayName = displayName;
    }

    public static <E extends Enum<E>> E nextValue(E current) {
        E[] values = current.getDeclaringClass().getEnumConstants();
        return values[(current.ordinal() + 1) % values.length];
    }

    @Override
    public String toString() {
        return displayName;
    }
}
