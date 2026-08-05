package cx.arcane.managers.interactionManager.crystals;

import lombok.Data;
import org.bukkit.entity.Player;

@Data
public class CrystalUser {
    private final Player player;
    private volatile AnimationType lastAnimation;
    private volatile boolean ignoreAnimation;

    public CrystalUser(Player player, AnimationType lastAnimation, boolean ignoreAnimation) {
        this.player = player;
        this.lastAnimation = lastAnimation;
        this.ignoreAnimation = ignoreAnimation;
    }
}