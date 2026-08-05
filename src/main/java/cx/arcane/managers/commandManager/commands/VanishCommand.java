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

@Command(name = "vanish")
@Permission("arcane.rank.management")
public class VanishCommand {

    @Execute
    public void execute(@Sender Player pSender) {
        PlayerData pData = PlayerManager.getByUniqueId(pSender.getUniqueId());
        PlayerManager.setVanished(pSender, !PlayerManager.isVanished(pSender));

        Component res = Component.text().append(
                Component.text("Vanish", Colors.HOT_PINK),
                Component.text(" mode ", Colors.GRAY),
                Component.text(pData.getMeta().isVanish() ? "ON" : "OFF", Colors.HOT_PINK)
        ).build();

        pSender.sendMessage(res);
        pSender.sendActionBar(res);
        pSender.playSound(pSender.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 3f);
    }
}