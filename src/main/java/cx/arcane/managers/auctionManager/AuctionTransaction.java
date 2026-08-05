package cx.arcane.managers.auctionManager;

import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.UUID;

public final class AuctionTransaction {
    private final UUID id;
    private final Instant transactedAt;
    private final ItemStack item;
    private final long price;
    private final UUID buyerId;
    private final UUID sellerId;

    public AuctionTransaction(Instant transactedAt, ItemStack item, long price, UUID buyerId, UUID sellerId) {
        this.id = UUID.randomUUID();
        this.transactedAt = transactedAt;
        this.item = item.clone();
        this.price = price;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
    }

    public AuctionTransaction(UUID id, Instant transactedAt, ItemStack item, long price, UUID buyerId, UUID sellerId) {
        this.id = id;
        this.transactedAt = transactedAt;
        this.item = item.clone();
        this.price = price;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
    }

    public UUID getId()              { return id; }
    public Instant getTransactedAt() { return transactedAt; }
    public ItemStack getItem()       { return item; }
    public long getPrice()           { return price; }
    public UUID getBuyerId()         { return buyerId; }
    public UUID getSellerId()        { return sellerId; }
}