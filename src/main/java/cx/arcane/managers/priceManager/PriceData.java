package cx.arcane.managers.priceManager;

import cx.arcane.managers.itemManager.ItemManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

@Getter
public class PriceData {

    private final Material material;

    @Setter
    private long price;

    public PriceData(Material material) {
        this.material = material;
        this.price = 0L;
    }

    public String getName() {
        return ItemManager.getDisplayName(material);
    }

    public String getKey() {
        return ItemManager.getKey(material);
    }
}