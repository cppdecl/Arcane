package cx.arcane.managers.ordersManager;

import cx.arcane.utils.Colors;
import cx.arcane.utils.ItemCategory;
import cx.arcane.utils.ItemUtils;
import cx.arcane.utils.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class OrderListing {
    private final UUID id;
    private final UUID ownerId;
    private final Instant listedAt;
    private final ItemStack item;
    private final ItemCategory category;
    private final long amount;
    private final long price;

    private volatile long deliveredAmount;
    private volatile long collectedAmount;

    public OrderListing(UUID id, UUID ownerId, Instant listedAt,
                        ItemStack item, long amount, long deliveredAmount, long collectedAmount, long price) {
        this.id              = id;
        this.ownerId         = ownerId;
        this.listedAt        = listedAt;
        this.item            = item.clone();
        this.amount          = amount;
        this.deliveredAmount = deliveredAmount;
        this.collectedAmount = collectedAmount;
        this.price           = price;
        this.category        = ItemUtils.getCategory(item);
    }

    public UUID getId()               { return id; }
    public UUID getOwnerId()          { return ownerId; }
    public Instant getListedAt()      { return listedAt; }
    public ItemStack getItem()        { return item; }
    public ItemCategory getCategory() { return category; }
    public long getAmount()           { return amount; }
    public long getPrice()            { return price; }

    public synchronized long getDeliveredAmount() { return deliveredAmount; }
    public synchronized long getCollectedAmount() { return collectedAmount; }

    public synchronized void increaseDeliveredAmount(long delta) { deliveredAmount += delta; }
    public synchronized void increaseCollectedAmount(long delta)  { collectedAmount += delta; }

    public boolean isFulfilled() { return getDeliveredAmount() >= amount; }
    public boolean canDeliver()  { return !isFulfilled(); }
    public boolean canCollect()  { return getDeliveredAmount() > getCollectedAmount(); }
    public boolean isCollected() { return getCollectedAmount() >= getDeliveredAmount(); }
    public boolean canDelete()   { return isCollected(); }

    public @NotNull Component getStatusComponent() {
        if (isFulfilled())
            return Component.text("Order Complete!", Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false);
        long remaining = amount - getDeliveredAmount();
        return Component.text("Needs ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(Text.formatShortBalance(remaining) + " More", Colors.HOT_PINK));
    }
}