package cx.arcane.managers.chatManager;

import cx.arcane.managers.authManager.AuthManager;
import cx.arcane.managers.permissionManager.PermissionManager;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerSettings;
import cx.arcane.managers.publicBotManager.PublicBotManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import cx.arcane.utils.Text;
import dev.triumphteam.gui.GuiView;
import dev.triumphteam.gui.container.GuiContainer;
import dev.triumphteam.gui.element.GuiItem;
import dev.triumphteam.gui.layout.GuiLayout;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import dev.triumphteam.gui.slot.Slot;
import dev.triumphteam.gui.state.pagination.PagerState;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatListener implements Listener {

    @EventHandler
    public void onPlayerChat(AsyncChatEvent e) {
        Player p = e.getPlayer();

        e.setCancelled(true);

        if (!AuthManager.isAuthenticated(p.getUniqueId())) return;

        Component playerName = Component.text()
                .append(Text.stringToComponent(PlaceholderAPI.setPlaceholders(p, "%luckperms_prefix%") + p.getName()))
                .append(Text.stringToComponent(PlaceholderAPI.setPlaceholders(p, "%luckperms_suffix%")))
                .append(Text.stringToComponent("&r&#4C4866:&r "))
                .build();

        String baseColorHex = PermissionManager.getPermissionString(p, "arcane.color.chat", "&#ff0000");
        TextColor baseColor = Text.stringToComponent(baseColorHex).color();

        String rawText = PlainTextComponentSerializer.plainText().serialize(e.message());

        Component messageContent = PermissionManager.check(p, "arcane.rank.management")
                ? parseTokens(p, rawText, true)
                : parseTokens(p, rawText, false);

        Component message = Component.text()
                .color(baseColor)
                .append(messageContent)
                .build();

        Component output = Component.text()
                .append(playerName)
                .append(message)
                .build();

        /*if (rawText.toLowerCase(Locale.ROOT).contains("grok") || rawText.toLowerCase(Locale.ROOT).contains("ai")) {
            PublicBotManager.talk(Text.componentToString(output));
        }*/

        Log.info("[Chat] {}", PlainTextComponentSerializer.plainText().serialize(output));

        boolean isSpam = ChatManager.isChatSpam(p.getUniqueId(), rawText);

        for (Player target : Bukkit.getOnlinePlayers()) {
            boolean self = target == p;

            PlayerData pData = PlayerManager.getByUniqueId(target.getUniqueId());
            if (pData == null) continue;

            PlayerSettings pSettings = pData.getSettings();

            if (!AuthManager.isAuthenticated(target.getUniqueId())) continue;
            if (!pSettings.isShowPublicChats() && !self) continue;
            if (pSettings.isUseSpamFilter() && isSpam && !self) continue;

            target.sendMessage(output);
        }

        var chatInfo = new ChatManager.ChatInfo(p.getUniqueId(), rawText, Instant.now());
        ChatManager.getLastSentMessageCache().put(p.getUniqueId(), chatInfo);
        ChatManager.getMessageHistory().addLast(chatInfo);
    }

    private Component parseTokens(Player player, String text, boolean isColored) {

        Component comp = Component.text("");
        int index = 0;

        while (index < text.length()) {

            int start = text.indexOf('[', index);

            if (start == -1) {
                comp = comp.append(isColored
                        ? Text.stringToComponent(text.substring(index))
                        : Component.text(text.substring(index)));
                break;
            }

            int end = text.indexOf(']', start);

            if (end == -1) {
                comp = comp.append(isColored
                        ? Text.stringToComponent(text.substring(index))
                        : Component.text(text.substring(index)));
                break;
            }

            if (start > index) {
                String before = text.substring(index, start);
                comp = comp.append(isColored
                        ? Text.stringToComponent(before)
                        : Component.text(before));
            }

            String token = text.substring(start + 1, end).toLowerCase();

            switch (token) {

                case "hand":
                case "item":
                    comp = comp.append(getHandComponent(player));
                    break;

                case "inventory":
                case "inv":
                    comp = comp.append(getInventoryComponent(player));
                    break;

                case "ender":
                case "echest":
                case "enderchest":
                    comp = comp.append(getEnderComponent(player));
                    break;

                case "armor":
                    comp = comp.append(getArmorComponent(player));
                    break;

                default:
                    String raw = "[" + token + "]";
                    comp = comp.append(isColored
                            ? Text.stringToComponent(raw)
                            : Component.text(raw));
                    break;
            }

            index = end + 1;
        }

        return comp;
    }

    public static Component getHandComponent(Player player) {
        var item = player.getInventory().getItemInMainHand();

        if (item.getType().isAir()) {
            return Component.text("[Fists]", Colors.GRAY);
        }

        int amount = item.getAmount();

        Component itemNameComponent = Component.text(
                PlainTextComponentSerializer.plainText().serialize(item.effectiveName()),
                Colors.GRAY
        );

        if (item.getItemMeta().hasCustomName()) {
            itemNameComponent = item.effectiveName();
        }

        return Component.text()
                .append(Component.text("[", Colors.GRAY))
                .append(Component.text(amount + "x ", Colors.GRAY))
                .append(itemNameComponent)
                .append(Component.text("]", Colors.GRAY))
                .hoverEvent(HoverEvent.showText(Component.text("Click to Inspect Item", Colors.HOT_PINK)))
                .clickEvent(ClickEvent.callback(a -> {
                    Player viewer = (Player) a;

                    Gui gui = Gui.of(3)
                            .title(Component.text(player.getName() + "'s Hand"))
                            .statelessComponent(con -> {
                                ItemStack currentStack = item;
                                con.setItem(2, 5, ItemBuilder.from(currentStack).asGuiItem((p, ctx) -> {
                                    if (currentStack.getType().name().contains("SHULKER_BOX") || currentStack.getType().name().contains("BUNDLE")) {
                                        openShulkerOrBundle(viewer, currentStack, ctx.guiView());
                                    }
                                }));
                            })
                            .build();

                    gui.open(viewer);
                    viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                .build();
    }

    public static Component getArmorComponent(Player player) {

        return Component.text("[" + player.getName() + "'s Armor]", Colors.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text("Click to Inspect Armor", Colors.HOT_PINK)))
                .clickEvent(ClickEvent.callback(a -> {

                    Player viewer = (Player) a;

                    Gui gui = Gui.of(3)
                            .title(Component.text(player.getName() + "'s Armor"))
                            .statelessComponent(con -> {

                                con.setItem(2, 3,
                                        ItemBuilder.from(player.getEquipment().getHelmet()).asGuiItem());

                                con.setItem(2, 4,
                                        ItemBuilder.from(player.getEquipment().getChestplate()).asGuiItem());

                                con.setItem(2, 5,
                                        ItemBuilder.from(player.getEquipment().getLeggings()).asGuiItem());

                                con.setItem(2, 6,
                                        ItemBuilder.from(player.getEquipment().getBoots()).asGuiItem());
                            })
                            .build();

                    gui.open(viewer);
                    viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()));
    }

    public static Component getInventoryComponent(Player player) {
        return Component.text("[" + player.getName() + "'s Inventory]", Colors.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text("Click to Inspect Inventory", Colors.HOT_PINK)))
                .clickEvent(ClickEvent.callback(a -> {

                    Player viewer = (Player) a;

                    Gui gui = Gui.of(6)
                            .title(Component.text(player.getName() + "'s Inventory"))
                            .statelessComponent(con -> {

                                GuiItem<Player, ItemStack> border = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                                        .name(Component.empty())
                                        .asGuiItem();

                                ItemStack[] armor = player.getInventory().getArmorContents();
                                setInspectedItem(con, 1, 1, viewer, armor[3]);
                                setInspectedItem(con, 1, 2, viewer, armor[2]);
                                setInspectedItem(con, 1, 3, viewer, armor[1]);
                                setInspectedItem(con, 1, 4, viewer, armor[0]);

                                setInspectedItem(con, 1, 6, viewer, player.getInventory().getItemInOffHand());

                                con.setItem(1, 5, border);
                                for (int col = 7; col <= 9; col++) con.setItem(1, col, border);

                                ItemStack[] storage = player.getInventory().getStorageContents();

                                int slotIndex = 10;
                                for (int i = 9; i <= 35; i++) {
                                    setInspectedItem(con, (slotIndex / 9) + 1, (slotIndex % 9) + 1, viewer, storage[i]);
                                    slotIndex++;
                                }

                                for (int col = 1; col <= 9; col++) {
                                    con.setItem(5, col, border);
                                }

                                for (int i = 0; i <= 8; i++) {
                                    setInspectedItem(con, 6, i + 1, viewer, storage[i]);
                                }

                            })
                            .build();

                    gui.open(viewer);
                    viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()));
    }

    public static void setInspectedItem(GuiContainer<Player, ItemStack> con, int row, int col, Player viewer, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return;

        con.setItem(row, col, ItemBuilder.from(stack).asGuiItem((p, ctx) -> {
            if (stack.getType().name().contains("SHULKER_BOX") || stack.getType().name().contains("BUNDLE")) {
                openShulkerOrBundle(viewer, stack, ctx.guiView());
            }
        }));
    }

    public static Component getEnderComponent(Player player) {

        return Component.text("[" + player.getName() + "'s Ender Chest]", Colors.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text("Click to Inspect Ender Chest", Colors.HOT_PINK)))
                .clickEvent(ClickEvent.callback(a -> {

                    Player viewer = (Player) a;

                    var ender = player.getEnderChest();
                    var contents = ender.getContents();

                    int size = contents.length;
                    int rows = Math.max(1, Math.min(6, size / 9));

                    Gui gui = Gui.of(rows)
                            .title(Component.text(player.getName() + "'s Ender Chest"))
                            .statelessComponent(con -> {

                                for (int i = 0; i < contents.length && i < rows * 9; i++) {

                                    if (contents[i] != null && !contents[i].getType().isAir()) {
                                        ItemStack currentStack = contents[i];
                                        con.setItem(i, ItemBuilder.from(currentStack).asGuiItem((p, ctx) -> {
                                            if (currentStack.getType().name().contains("SHULKER_BOX") || currentStack.getType().name().contains("BUNDLE")) {
                                                openShulkerOrBundle(viewer, currentStack, ctx.guiView());
                                            }
                                        }));
                                    }

                                }

                            })
                            .build();

                    gui.open(viewer);
                    viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()));
    }

    public static void openShulkerOrBundle(Player viewer, ItemStack item, GuiView parentGui) {
        if (item == null || item.getType().isAir()) return;

        boolean isShulker = item.getType().name().contains("SHULKER_BOX");
        boolean isBundle = item.getType().name().contains("BUNDLE");

        if (!isShulker && !isBundle) return;

        List<ItemStack> contents = new ArrayList<>();
        if (isShulker) {
            BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
            ShulkerBox box = (ShulkerBox) meta.getBlockState();
            for (ItemStack i : box.getInventory().getContents()) {
                if (i != null && !i.getType().isAir()) contents.add(i);
            }
        } else {
            BundleMeta meta = (BundleMeta) item.getItemMeta();
            for (ItemStack i : meta.getItems()) {
                if (i != null && !i.getType().isAir()) contents.add(i);
            }
        }

        final var navigating = new boolean[]{false};

        int maxContentRows = 3;
        int itemsPerPage = maxContentRows * 7;
        boolean paginated = contents.size() > itemsPerPage;

        int contentRows = paginated
                ? maxContentRows
                : Math.max(1, (int) Math.ceil(contents.size() / 7.0));

        int rows = contentRows + 2;
        int totalPages = (contents.size() + itemsPerPage - 1) / itemsPerPage;

        Gui subGui = Gui.of(rows)
                .title(item.effectiveName().color(NamedTextColor.DARK_GRAY))
                .onClose(() -> {
                    if (!navigating[0]) {
                        parentGui.open();
                        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                    }
                })
                .component(component -> {

                    PagerState<ItemStack> pageState = null;

                    if (paginated) {
                        pageState = PagerState.of(
                                contents,
                                GuiLayout.box(Slot.of(2, 2), Slot.of(rows - 1, 8))
                        );
                        component.remember(pageState);
                    }

                    PagerState<ItemStack> finalPageState = pageState;

                    component.render(con -> {

                        if (paginated) {
                            finalPageState.forEach(entry -> {
                                ItemStack currentStack = entry.element();
                                con.setItem(entry.slot(), createContainerItem(viewer, currentStack, navigating));
                            });

                            if (finalPageState.getCurrentPage() > 1) {
                                con.setItem(rows, 1, prevItemButton(viewer, finalPageState));
                            }

                            if (finalPageState.getCurrentPage() < totalPages && totalPages > 1) {
                                con.setItem(rows, 9, nextItemButton(viewer, finalPageState));
                            }

                        } else {
                            int index = 0;
                            for (ItemStack currentStack : contents) {
                                int col = (index % 7) + 2;
                                int row = (index / 7) + 2;
                                con.setItem(row, col, createContainerItem(viewer, currentStack, navigating));
                                index++;
                            }
                        }

                        con.setItem(rows, 5, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                                .name(Text.toSmallCapsComponent("Back")
                                        .color(TextColor.color(0xff0000))
                                        .decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to return", Colors.WHITE)
                                        .decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    parentGui.open();
                                    viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                                }));
                    });
                })
                .build();

        subGui.open(viewer);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
    }

    public static GuiItem<Player, ItemStack> createContainerItem(Player viewer, ItemStack stack, boolean[] navigating) {
        return ItemBuilder.from(stack).asGuiItem((p, ctx) -> {
            if (stack.getType().name().contains("SHULKER_BOX") || stack.getType().name().contains("BUNDLE")) {
                navigating[0] = true;
                openShulkerOrBundle(viewer, stack, ctx.guiView());
            }
        });
    }

    public static GuiItem<Player, ItemStack> prevItemButton(Player p, PagerState<ItemStack> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Previous")
                        .color(Colors.HOT_PINK)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to go to previous page", Colors.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1, 1);
                    state.prev();
                });
    }

    public static GuiItem<Player, ItemStack> nextItemButton(Player p, PagerState<ItemStack> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Next")
                        .color(Colors.HOT_PINK)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to go to next page", Colors.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1, 1);
                    state.next();
                });
    }
}