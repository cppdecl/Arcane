package cx.arcane.managers.skinManager;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Data
public class CachedSkin {
    private UUID uniqueId;

    private String skinData;
    private String skinSignature;

    public CachedSkin() {}

    public CachedSkin(@NotNull UUID uniqueId, String name) {
    }
}
