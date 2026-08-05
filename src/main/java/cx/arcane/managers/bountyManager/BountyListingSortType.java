package cx.arcane.managers.bountyManager;

public enum BountyListingSortType {
    HIGHEST_PRICE("Highest Reward"),
    LOWEST_PRICE("Lowest Reward");

    private final String displayName;

    BountyListingSortType(String displayName) {
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
