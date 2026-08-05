package cx.arcane.managers.commandManager.arguments.onlinePlayerExceptMeArgument;

import cx.arcane.managers.playerManager.PlayerData;
import org.jetbrains.annotations.NotNull;

public class OnlinePlayerExceptMeArgument {
    private final PlayerData playerData;

    public OnlinePlayerExceptMeArgument(PlayerData playerData) {
        this.playerData = playerData;
    }

    public @NotNull PlayerData get() {
        return playerData;
    }
}
