package cx.arcane.managers.commandManager.commands;

import cx.arcane.Arcane;
import cx.arcane.managers.authManager.AuthManager;
import cx.arcane.managers.authManager.AuthState;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.CryptUtils;
import cx.arcane.utils.PlayerUtils;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

@Command(name = "login")
public class LoginCommand {

    @Execute
    public void execute(@Sender Player player, @Arg String password) {

        AuthState aState = AuthManager.getAuthSession(player.getUniqueId());
        if (aState == null || aState.isAuthenticated()) {
            return;
        }

        PlayerData pData = PlayerManager.getByUniqueId(player.getUniqueId());
        if (pData == null) {
            PlayerUtils.sendPacketMessage(player, Component.text("You are not registered. Use /register <password>.", Colors.DARK_PINK));
            return;
        }

        if (!CryptUtils.validatePassword(password, pData.getPassword())) {
            PlayerUtils.sendPacketMessage(player, Component.text("Incorrect password. Try again.", Colors.DARK_PINK));
            return;
        }

        PlayerManager.updatePlayer(player, false);

        player.setInvisible(false);
        PlayerUtils.sendPacketRemovePotionEffect(player, PotionEffectType.BLINDNESS);

        PlayerUtils.sendPacketMessage(player, Component.text("Welcome back, " + player.getName() + "!", Colors.HOT_PINK));

        AuthManager.authenticate(player);

        PlayerUtils.sendPacketTitle(player, Title.title(Component.text(""), Component.text(""), 0, 0, 0));
        PlayerUtils.sendPacketActionBar(player, Component.text(""));
    }
}