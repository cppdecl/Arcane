package cx.arcane.managers.commandManager.commands;

import cx.arcane.Arcane;
import cx.arcane.managers.authManager.AuthManager;
import cx.arcane.managers.authManager.AuthState;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.listeners.PlayerListener;
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

import java.util.regex.Pattern;

@Command(name = "register")
public class RegisterCommand {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^[A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]{3,64}$"
    );

    @Execute
    public void execute(@Sender Player player, @Arg String password) {

        AuthState aState = AuthManager.getAuthSession(player.getUniqueId());
        if (aState == null || aState.isAuthenticated()) {
            return;
        }

        if (PlayerManager.getByUniqueId(player.getUniqueId()) != null) {
            PlayerUtils.sendPacketMessage(player, Component.text("You are already registered. Use /login <password>.", Colors.DARK_PINK));
            return;
        }

        if (password.length() < 3 || password.length() > 64 || !PASSWORD_PATTERN.matcher(password).matches()) {
            PlayerUtils.sendPacketMessage(player, Component.text("Password must be 3–64 characters and does not contain illegal characters.", Colors.DARK_PINK));
            return;
        }

        PlayerUtils.sendPacketTitle(player, Title.title(Component.text(""), Component.text(""), 0, 0, 0));
        PlayerUtils.sendPacketActionBar(player, Component.text(""));

        PlayerData pData = PlayerManager.newPlayer(player, CryptUtils.hashPassword(password));
        PlayerUtils.sendPacketMessage(player, Component.text("You have registered successfully.", Colors.HOT_PINK));
        aState.setAuthenticated(true);

        player.setInvisible(false);
        PlayerUtils.sendPacketRemovePotionEffect(player, PotionEffectType.BLINDNESS);

        PlayerListener.onJoin(player);
    }
}