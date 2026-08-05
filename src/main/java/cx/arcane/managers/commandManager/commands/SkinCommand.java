package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.skinManager.SkinManager;
import cx.arcane.utils.Colors;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command(name = "skin")
public class SkinCommand {

    @Execute(name = "set")
    public void setSelf(@Sender CommandSender sender, @Arg String skinName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Console must use /skin set <player> <skin>");
            return;
        }
        sendProcessing(player);
        SkinManager.setSkinCommand(sender, player, skinName);
    }

    @Execute(name = "update")
    public void updateSelf(@Sender CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        sendProcessing(player);
        SkinManager.clearSkinCommand(sender, player);
    }

    @Execute(name = "clear")
    public void clearSelf(@Sender CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        sendProcessing(player);
        SkinManager.clearSkinCommand(sender, player);
    }

    @Execute(name = "set")
    @Permission("arcane.rank.management")
    public void setOther(@Sender CommandSender sender, @Arg Player target, @Arg String skinName) {
        SkinManager.setSkinCommand(sender, target, skinName);
    }

    @Execute(name = "clear")
    @Permission("arcane.rank.management")
    public void clearOther(@Sender CommandSender sender, @Arg Player target) {
        SkinManager.clearSkinCommand(sender, target);
    }

    @Execute
    public void defaultSet(@Sender CommandSender sender, @Arg String skinName) {
        setSelf(sender, skinName);
    }

    private void sendProcessing(Player player) {
        player.sendMessage(Component.text("Updating skin...", Colors.HOT_PINK));
        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1f, 0.0f);
    }
}