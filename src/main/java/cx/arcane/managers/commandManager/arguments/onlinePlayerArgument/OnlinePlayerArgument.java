package cx.arcane.managers.commandManager.arguments.onlinePlayerArgument;

import cx.arcane.managers.playerManager.PlayerData;
import org.jetbrains.annotations.NotNull;

public class OnlinePlayerArgument {
    private final PlayerData playerData;

    public OnlinePlayerArgument(PlayerData playerData) {
        this.playerData = playerData;
    }

    public @NotNull PlayerData get() {
        return playerData;
    }
}
