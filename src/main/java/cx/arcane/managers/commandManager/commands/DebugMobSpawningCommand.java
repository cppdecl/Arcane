package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

@Command(name = "dmobs", aliases = {"dm"})
@Permission("arcane.rank.management")
public class DebugMobSpawningCommand {

    @Execute
    public void execute(@Sender Player sender) {
        PlayerData pData = PlayerManager.getByUniqueId(sender.getUniqueId());
        pData.getMeta().setMobSpawningDebug(!pData.getMeta().isMobSpawningDebug());
        TextComponent msg = Component.text("Debugging Mob Spawns: ", Colors.GRAY).append(pData.getMeta().isMobSpawningDebug() ? Component.text("ON", Colors.HOT_PINK) : Component.text("OFF", Colors.RED));
        sender.sendActionBar(msg);
        sender.playSound(sender.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
    }
}