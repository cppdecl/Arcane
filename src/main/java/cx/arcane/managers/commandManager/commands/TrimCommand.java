package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.commandManager.arguments.trimMaterialArgument.TrimMaterialArgument;
import cx.arcane.managers.commandManager.arguments.trimPatternArgument.TrimPatternArgument;
import cx.arcane.utils.Colors;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;

@Command(name = "trim")
@Permission("arcane.rank.management")
public class TrimCommand {

    @Execute
    public void removeTrim(@Sender Player sender) {
        ItemStack item = sender.getInventory().getItemInMainHand();
        if (!(item.getItemMeta() instanceof ArmorMeta meta)) {
            sendError(sender, "You must be holding a piece of armor.");
            return;
        }

        if (!meta.hasTrim()) {
            sendError(sender, "This item has no trim.");
            return;
        }

        meta.setTrim(null);
        item.setItemMeta(meta);

        sendSuccess(sender, "Removed Armor Trim.");
    }

    @Execute
    public void applyTrim(@Sender Player sender,
                          @Arg TrimPatternArgument pattern,
                          @Arg TrimMaterialArgument material) {

        ItemStack item = sender.getInventory().getItemInMainHand();
        if (!(item.getItemMeta() instanceof ArmorMeta meta)) {
            sendError(sender, "You must be holding a piece of armor.");
            return;
        }

        meta.setTrim(new ArmorTrim(material.get(), pattern.get()));
        item.setItemMeta(meta);

        Component msg = Component.text("Applied ", Colors.GRAY)
                .append(Component.text(pattern.getName(), Colors.HOT_PINK))
                .append(Component.text(" Armor Trim with ", Colors.GRAY))
                .append(Component.text(material.getName(), Colors.HOT_PINK))
                .append(Component.text(" to your armor.", Colors.GRAY));

        sender.sendMessage(msg);
        sender.sendActionBar(msg);
        sender.playSound(sender.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.1f);
    }

    private void sendError(Player player, String message) {
        Component msg = Component.text(message, Colors.DARK_PINK);
        player.sendMessage(msg);
        player.sendActionBar(msg);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }

    private void sendSuccess(Player player, String message) {
        Component msg = Component.text(message, Colors.GRAY);
        player.sendMessage(msg);
        player.sendActionBar(msg);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.1f);
    }
}