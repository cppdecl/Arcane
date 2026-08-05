package cx.arcane.managers.commandManager.arguments.trimPatternArgument;

import lombok.Getter;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jetbrains.annotations.NotNull;

public class TrimPatternArgument {
    private final TrimPattern pattern;
    @Getter
    private final String name;

    public TrimPatternArgument(TrimPattern pattern, String name) {
        this.pattern = pattern; this.name = name;
    }

    public @NotNull TrimPattern get() {
        return pattern;
    }
}