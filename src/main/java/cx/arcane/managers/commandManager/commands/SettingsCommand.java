package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerSettings;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Text;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import dev.triumphteam.gui.container.GuiContainer;
import dev.triumphteam.gui.element.GuiItem;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import dev.triumphteam.nova.MutableState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

@Command(name = "settings")
public class SettingsCommand {

    @Execute
    public void execute(@Sender Player pSender) {
        pSender.playSound(pSender.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);
        PlayerData pData = PlayerManager.getByUniqueId(pSender.getUniqueId());

        final var gui = Gui.of(6)
                .title(Text.toSmallCapsComponent("Settings"))
                .component(component -> {
                    PlayerSettings settings = pData.getSettings();

                    final var showPublicChats = component.remember(settings.isShowPublicChats());
                    final var showPrivateMessages = component.remember(settings.isShowPrivateMessages());
                    final var showDeathMessages = component.remember(settings.isShowDeathMessages());
                    final var showSystemMessages = component.remember(settings.isShowSystemMessages());
                    final var showPunishmentMessages = component.remember(settings.isShowPunishmentMessages());
                    final var useProfanityFilter = component.remember(settings.isUseProfanityFilter());
                    final var useSpamFilter = component.remember(settings.isUseSpamFilter());
                    final var allowTpaRequests = component.remember(settings.isAllowTpaRequests());
                    final var autoAcceptTpaRequests = component.remember(settings.isAutoAcceptTpaRequests());
                    final var autoAcceptTpaHereRequests = component.remember(settings.isAutoAcceptTpaHereRequests());
                    final var allowPayments = component.remember(settings.isAllowPayments());
                    final var nightVision = component.remember(settings.isNightVision());
                    final var allowMobSpawning = component.remember(settings.isAllowMobSpawning());

                    component.render(container -> {

                        option(container, 2, 2, Material.OAK_SIGN, "Public Chats", showPublicChats, settings::setShowPublicChats);
                        option(container, 2, 3, Material.BIRCH_SIGN, "Private Messages", showPrivateMessages, settings::setShowPrivateMessages);
                        option(container, 2, 4, Material.BAMBOO_SIGN, "Death Messages", showDeathMessages, settings::setShowDeathMessages);
                        option(container, 2, 5, Material.JUNGLE_SIGN, "System Messages", showSystemMessages, settings::setShowSystemMessages);
                        option(container, 2, 6, Material.ACACIA_SIGN, "Punishment Messages", showPunishmentMessages, settings::setShowPunishmentMessages);
                        option(container, 2, 7, Material.SPRUCE_SIGN, "Profanity Filter", useProfanityFilter, settings::setUseProfanityFilter);
                        option(container, 2, 8, Material.DARK_OAK_SIGN, "Spam Filter", useSpamFilter, settings::setUseSpamFilter);

                        option(container, 3, 2, Material.ENDER_PEARL, "TPA Requests", allowTpaRequests, settings::setAllowTpaRequests);
                        option(container, 3, 3, Material.IRON_NAUTILUS_ARMOR, "Auto Accept TPA Requests", autoAcceptTpaRequests, settings::setAutoAcceptTpaRequests);
                        option(container, 3, 4, Material.NETHERITE_NAUTILUS_ARMOR, "Auto Accept TPA Here Requests", autoAcceptTpaHereRequests, settings::setAutoAcceptTpaHereRequests);
                        option(container, 3, 5, Material.DIAMOND, "Allow Payments", allowPayments, settings::setAllowPayments);
                        option(container, 3, 6, Material.GLOW_INK_SAC, "Night Vision", nightVision, settings::setNightVision);
                        option(container, 3, 7, Material.PIG_SPAWN_EGG, "Allow Mob Spawning", allowMobSpawning, settings::setAllowMobSpawning);
                    });
                })
                .build();

        gui.open(pSender);

    }

    private static void option(
            GuiContainer<Player, ItemStack> container,
            int row, int column,
            Material material,
            String title,
            MutableState<Boolean> state,
            Consumer<Boolean> applyToSettings
    ) {
        Component displayName = Component.text(Text.toSmallCaps(title), Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false);

        GuiItem<Player, ItemStack> guiItem = ItemBuilder.from(material)
                .name(displayName)
                .lore(Component.text("Currently: ", Colors.WHITE).decoration(TextDecoration.ITALIC, false).append(Component.text(state.get() ? "On" : "Off", state.get() ? Colors.GREEN : Colors.RED).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true)))
                .asGuiItem((player, context) -> {
                    state.update(prev -> !prev);
                    applyToSettings.accept(state.get());
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                });

        container.setItem(row, column, guiItem);
    }
}