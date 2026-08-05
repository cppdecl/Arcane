package cx.arcane.managers.auctionManager;

import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.ItemCategory;
import cx.arcane.utils.ItemUtils;
import org.bukkit.inventory.ItemStack;
import java.time.Instant;
import java.util.UUID;

public final class AuctionListing {
    private final UUID id;
    private final UUID ownerId;
    private final Instant listedAt;
    private final ItemStack item;
    private final ItemCategory category;
    private final long price;

    public AuctionListing(UUID id, UUID ownerId, Instant listedAt, ItemStack item, long price) {
        this.id = id;
        this.ownerId = ownerId;
        this.listedAt = listedAt;
        this.item = item.clone();
        this.price = price;
        this.category = ItemUtils.getCategory(item);
    }

    public UUID getId()               { return id; }
    public UUID getOwnerId()          { return ownerId; }
    public Instant getListedAt()      { return listedAt; }
    public ItemStack getItem()        { return item; }
    public ItemCategory getCategory() { return category; }
    public long getPrice()            { return price; }

    public String getOwnerName() { return PlayerManager.getName(getOwnerId()); }
}