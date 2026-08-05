package cx.arcane.managers.ecoManager;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EcoData {
    private UUID uniqueId;
    private long money;

    public void giveMoney(long amount) {
        this.money += amount;
    }

    public void takeMoney(long amount) {
        this.money -= amount;
        if (this.money < 0) this.money = 0;
    }
}