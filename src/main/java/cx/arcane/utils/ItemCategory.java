package cx.arcane.utils;

public enum ItemCategory {
    ALL("All"),
    BLOCKS("Blocks"),
    TOOLS("Tools"),
    FOOD("Food"),
    COMBAT("Combat"),
    POTIONS("Potions"),
    BOOKS("Books"),
    INGREDIENTS("Ingredients"),
    UTILITIES("Utilities");

    public static <E extends Enum<E>> E nextValue(E current) {
        E[] values = current.getDeclaringClass().getEnumConstants();
        return values[(current.ordinal() + 1) % values.length];
    }

    private final String displayName;

    ItemCategory(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
