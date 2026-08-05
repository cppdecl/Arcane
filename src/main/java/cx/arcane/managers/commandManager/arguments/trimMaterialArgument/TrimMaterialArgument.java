package cx.arcane.managers.commandManager.arguments.trimMaterialArgument;

import lombok.Getter;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.jetbrains.annotations.NotNull;

public class TrimMaterialArgument {
    private final TrimMaterial material;
    @Getter
    private final String name;

    public TrimMaterialArgument(TrimMaterial material, String name) {
        this.material = material;
        this.name = name;
    }

    public @NotNull TrimMaterial get() {
        return material;
    }
}