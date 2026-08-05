package cx.arcane.managers.commandManager.arguments.playerArgument;

import cx.arcane.managers.playerManager.PlayerData;

public class PlayerArgument {

    private final PlayerData playerData;

    public PlayerArgument(PlayerData playerData) {
        this.playerData = playerData;
    }

    public PlayerData get() {
        return playerData;
    }
}
