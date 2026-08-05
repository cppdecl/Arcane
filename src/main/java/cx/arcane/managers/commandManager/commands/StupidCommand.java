package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.commandManager.arguments.playerArgument.PlayerArgument;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.PlayerUtils;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.stream.Collectors;

@Command(name = "stupid")
@Permission("arcane.rank.management")
public class StupidCommand {

    @Execute
    public void list(@Sender Player sender) {
        // Replace this with whatever your PlayerManager exposes
        List<PlayerData> stupidPlayers = PlayerManager.getAll().stream()
                .filter(data -> data.getMeta().isStupid())
                .toList();

        if (stupidPlayers.isEmpty()) {
            sender.sendMessage(Component.text("No stupid players right now.", Colors.GRAY));
            sender.playSound(sender.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
            return;
        }

        sender.sendMessage(Component.text("Stupid players:", Colors.HOT_PINK));

        for (PlayerData data : stupidPlayers) {
            sender.sendMessage(Component.text(" - " + data.getUsername(), Colors.GRAY));
        }

        sender.playSound(sender.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
    }

    @Execute(name = "toggle")
    public void toggle(@Sender Player sender, @Arg PlayerArgument targetArg) {
        PlayerData targetData = targetArg.get();

        boolean newValue = !targetData.getMeta().isStupid();
        targetData.getMeta().setStupid(newValue);

        Component res = Component.text()
                .append(Component.text("Stupid", Colors.HOT_PINK))
                .append(Component.text(" mode ", Colors.GRAY))
                .append(Component.text(newValue ? "ON" : "OFF", Colors.HOT_PINK))
                .append(Component.text(" for ", Colors.GRAY))
                .append(Component.text(targetData.getUsername(), Colors.HOT_PINK))
                .build();

        sender.sendMessage(res);
        sender.sendActionBar(res);
        sender.playSound(sender.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 3f);

        if (!targetData.getMeta().isStupid() && targetData.getPlayer() != null) {
            PlayerUtils.sendPacketRemovePotionEffect(targetData.getPlayer(), PotionEffectType.BLINDNESS);
            PlayerUtils.sendPacketRemovePotionEffect(targetData.getPlayer(), PotionEffectType.NAUSEA);
            PlayerUtils.sendPacketRemovePotionEffect(targetData.getPlayer(), PotionEffectType.DARKNESS);
            PlayerUtils.sendPacketRemovePotionEffect(targetData.getPlayer(), PotionEffectType.SLOWNESS);
            PlayerUtils.sendPacketActionBar(targetData.getPlayer(), Component.text(""));
            PlayerUtils.sendPacketTitle(targetData.getPlayer(), Title.title(Component.text(""), Component.text(""), 0, 0, 0));
        }
    }
}