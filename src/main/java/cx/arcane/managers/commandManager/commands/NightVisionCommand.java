package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

@Command(name = "nv")
public class NightVisionCommand {

    @Execute
    public void execute(@Sender Player pSender) {
        PlayerData pData = PlayerManager.getByUniqueId(pSender.getUniqueId());
        pData.getSettings().setNightVision(!pData.getSettings().isNightVision());

        Component res = Component.text().append(
                Component.text("Night vision", Colors.HOT_PINK),
                Component.text(" mode has been ", Colors.GRAY),
                Component.text(pData.getSettings().isNightVision() ? "enabled" : "disabled", Colors.HOT_PINK)
        ).build();

        pSender.sendMessage(res);
        pSender.sendActionBar(res);
        pSender.playSound(pSender.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 3f);
    }
}