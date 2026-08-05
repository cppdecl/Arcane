package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.commandManager.arguments.enchantmentArgument.EnchantmentArgument;
import cx.arcane.utils.Colors;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@Command(name = "enchant")
@Permission("arcane.rank.management")
public class EnchantCommand {

    @Execute
    public void enchant(
            @Sender Player sender,
            @Arg EnchantmentArgument enchantment
    ) {
        applyEnchant(sender, enchantment, 1);
    }

    @Execute
    public void enchantWithLevel(
            @Sender Player sender,
            @Arg EnchantmentArgument enchantment,
            @Arg int level
    ) {
        applyEnchant(sender, enchantment, level);
    }

    private void applyEnchant(Player sender, EnchantmentArgument enchant, int level) {
        ItemStack item = sender.getInventory().getItemInMainHand();

        if (item.getType().isAir()) {
            sendError(sender, "You must be holding an item to enchant it!");
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            sendError(sender, "Failed to get item metadata.");
            return;
        }

        boolean illegal = false;
        String action;

        if (level == 0) {
            meta.removeEnchant(enchant.get());
            action = "Removed";
        } else {
            meta.addEnchant(enchant.get(), level, true);
            action = "Applied";

            if (level > enchant.get().getMaxLevel() || !enchant.get().canEnchantItem(item)) {
                illegal = true;
            }
        }

        item.setItemMeta(meta);

        Component msg = Component.text(action + " enchantment: ", Colors.GRAY)
                .append(Component.text(enchant.getName(), Colors.HOT_PINK))
                .append(Component.text(" level ", Colors.GRAY))
                .append(Component.text(level, Colors.HOT_PINK));

        if (illegal) {
            msg = msg.append(Component.text(" (ILLEGAL!)", Colors.DARK_PINK));
        }

        sender.sendMessage(msg);
        sender.sendActionBar(msg);
        sender.playSound(sender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void sendError(Player player, String message) {
        TextComponent msg = Component.text(message, Colors.DARK_PINK);
        player.sendMessage(msg);
        player.sendActionBar(msg);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }
}