package cx.arcane.managers.commandManager.commands;

import cx.arcane.Arcane;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerMeta;
import cx.arcane.managers.priceManager.PriceManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import cx.arcane.utils.PlayerUtils;
import cx.arcane.utils.Text;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Command(name = "sell")
public class SellCommand implements Listener {

    public SellCommand() {
        Bukkit.getPluginManager().registerEvents(this, Arcane.getPlugin());
    }

    @Execute
    public void execute(@Sender Player pSender) {
        SellHolder holder = new SellHolder(pSender);
        Inventory inv = Bukkit.createInventory(holder, 36, Text.toSmallCapsComponent("Place Items To Sell"));
        holder.setInventory(inv);
        pSender.openInventory(inv);
        pSender.playSound(pSender.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {

        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) return;

        if (holder.isProcessed()) return;

        holder.setProcessed(true);

        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR)
                contents.add(item.clone());
        }

        event.getInventory().clear();

        if (contents.isEmpty()) return;

        processSell((Player) event.getPlayer(), contents);
    }

    private void processSell(Player pPlayer, List<ItemStack> pItems) {

        Collection<ItemStack> toReturn = new ArrayList<>();

        long total = 0;
        for (ItemStack item : pItems) {
            if (item == null) continue;
            long price = PriceManager.getSellPrice(item);
            if (price == 0) {
                toReturn.add(item);
                continue;
            }

            total += price;
        }

        PlayerUtils.giveOrDrop(pPlayer, toReturn);

        if (total <= 0) return;

        PlayerMeta pMeta = PlayerManager.getByUniqueId(pPlayer.getUniqueId()).getMeta();
        pMeta.setTotalSold(pMeta.getTotalSold() + total);

        EcoManager.giveMoney(pPlayer.getUniqueId(), total);
        pPlayer.playSound(pPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 2.0f);

        Component msg = Component.text("Received ", Colors.GRAY)
                .append(Component.text("$" + Text.formatShortBalance(total), Colors.GREEN));

        Component msg2 = Component.text("+$" + Text.formatShortBalance(total), Colors.GREEN);

        pPlayer.sendMessage(msg);
        pPlayer.sendActionBar(msg2);
    }

    @Data
    public static class SellHolder implements InventoryHolder {

        private final Player owner;
        private Inventory inventory;
        private boolean processed = false;

        public SellHolder(Player pOwner) {
            this.owner = pOwner;
        }
    }
}