package cx.arcane.utils;

import cx.arcane.Arcane;
import cx.arcane.managers.priceManager.PriceManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Text;
import dev.triumphteam.gui.GuiView;
import dev.triumphteam.gui.click.action.EmptyGuiClickAction;
import dev.triumphteam.gui.element.GuiItem;
import dev.triumphteam.gui.layout.GuiLayout;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import dev.triumphteam.gui.slot.Slot;
import dev.triumphteam.gui.state.pagination.PagerState;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ItemPicker {

    private static final List<Material> BASE_MATERIALS = Registry.MATERIAL.stream()
            .filter(mat -> mat.isItem() && !mat.isAir() && !mat.isLegacy())
            .toList();

    private static final Map<ItemPickerSortType, Comparator<Material>> SORT_COMPARATORS = Map.of(
            ItemPickerSortType.A_Z,           Comparator.comparing(Material::name),
            ItemPickerSortType.Z_A,           Comparator.comparing(Material::name).reversed(),
            ItemPickerSortType.HIGHEST_PRICE, Comparator.comparingDouble((Material m) -> PriceManager.getSellPrice(m)).reversed().thenComparing(Material::name),
            ItemPickerSortType.LOWEST_PRICE,  Comparator.comparingDouble((Material m) -> PriceManager.getSellPrice(m)).thenComparing(Material::name)
    );

    private String title = "Item Picker";
    private ItemPickerSortType sortType = ItemPickerSortType.A_Z;
    private ItemCategory category = ItemCategory.ALL;
    private String searchQuery = "";
    private boolean adminMode = false;
    private Consumer<PickAction> callback = null;
    private Runnable onQuit = null;

    public ItemPicker() {}

    public ItemPicker title(String title) {
        if (title != null && !title.isBlank()) this.title = title;
        return this;
    }

    public ItemPicker quit(Runnable onQuit) {
        this.onQuit = onQuit;
        return this;
    }

    public ItemPicker then(Consumer<PickAction> callback) {
        this.callback = callback;
        return this;
    }

    public ItemPicker admin() {
        this.adminMode = true;
        return this;
    }

    private static void schedule(Player p, Runnable task) {
        FoliaScheduler.getEntityScheduler().execute(p, Arcane.getPlugin(), task, null, 1L);
    }

    private List<Material> getFiltered() {
        return BASE_MATERIALS.stream()
                .filter(mat -> adminMode || !ItemUtils.isAdminMaterial(mat))
                .filter(mat -> category == ItemCategory.ALL || ItemUtils.getCategory(new ItemStack(mat)) == category)
                .filter(mat -> searchQuery == null || searchQuery.isBlank() || ItemUtils.matchesQuery(new ItemStack(mat), searchQuery))
                .sorted(SORT_COMPARATORS.getOrDefault(sortType, Comparator.comparing(Material::name)))
                .toList();
    }

    public void open(Player p) {
        buildGui(p);
    }

    private void buildGui(Player p) {
        List<Material> filtered = getFiltered();
        int rows = 6;
        int itemsPerPage = 9 * 5;
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) itemsPerPage));
        final var navigating = new boolean[]{false};

        var sortRef = new Object() { ItemPickerSortType value = sortType; };
        var catRef  = new Object() { ItemCategory value = category; };
        var queryRef = new Object() { String value = searchQuery; };

        Gui.of(rows)
                .title(Text.toSmallCapsComponent(title))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0] && onQuit != null) onQuit.run();
                })
                .component(component -> {
                    PagerState<Material> pageState = PagerState.of(
                            filtered,
                            GuiLayout.box(Slot.of(1, 1), Slot.of(5, 9))
                    );
                    component.remember(pageState);

                    component.render(con -> {
                        pageState.forEach(entry -> {
                            Material mat = entry.element();
                            ItemStack stack = new ItemStack(mat);

                            List<Component> lore = new ArrayList<>();
                            if (stack.hasItemMeta() && stack.getItemMeta().hasLore() && stack.getItemMeta().lore() != null) {
                                lore.addAll(stack.getItemMeta().lore());
                                lore.add(Component.empty());
                            }
                            lore.add(Component.text("(Click to Select)").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

                            con.setItem(entry.slot(), ItemBuilder.from(stack)
                                    .lore(lore)
                                    .flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
                                    .asGuiItem((player, ctx) -> {
                                        player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                        List<Enchantment> enchants = ItemUtils.getCompatibleEnchantments(stack);
                                        if (enchants.isEmpty()) {
                                            processPick(player, new ItemStack(mat));
                                        } else {
                                            navigating[0] = true;
                                            schedule(player, () -> openEnchantmentGui(player, new ItemStack(mat), enchants, ctx.guiView()));
                                        }
                                    }));
                        });

                        int page = pageState.getCurrentPage();
                        if (page > 1)          con.setItem(6, 1, prevButton(p, pageState));
                        if (page < totalPages) con.setItem(6, 9, nextButton(p, pageState));

                        con.setItem(6, 3, ItemBuilder.from(Material.CAULDRON)
                                .name(Text.toSmallCapsComponent("Sort").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Arrays.stream(ItemPickerSortType.values())
                                        .map(s -> (Component) Text.toSmallCapsComponent("▪ " + s)
                                                .color(sortRef.value == s ? Colors.HOT_PINK : NamedTextColor.WHITE)
                                                .decoration(TextDecoration.ITALIC, false))
                                        .collect(Collectors.toList()))
                                .asGuiItem((player, ctx) -> {
                                    sortType = ItemPickerSortType.nextValue(sortType);
                                    sortRef.value = sortType;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    navigating[0] = true;
                                    schedule(player, () -> buildGui(player));
                                }));

                        con.setItem(6, 4, ItemBuilder.from(Material.HOPPER)
                                .name(Text.toSmallCapsComponent("Filter").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Arrays.stream(ItemCategory.values())
                                        .map(c -> (Component) Text.toSmallCapsComponent("▪ " + c)
                                                .color(catRef.value == c ? Colors.HOT_PINK : NamedTextColor.WHITE)
                                                .decoration(TextDecoration.ITALIC, false))
                                        .collect(Collectors.toList()))
                                .asGuiItem((player, ctx) -> {
                                    category = ItemCategory.nextValue(category);
                                    catRef.value = category;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    navigating[0] = true;
                                    schedule(player, () -> buildGui(player));
                                }));

                        con.setItem(6, 6, ItemBuilder.from(Material.OAK_SIGN)
                                .name(Text.toSmallCapsComponent("Search").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                                .lore(Component.text("Click to search").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> {
                                    navigating[0] = true;
                                    player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                    schedule(player, () -> new SignGUI()
                                            .lines(List.of(
                                                    Component.text(""),
                                                    Component.text("↑↑↑↑↑↑↑↑").color(Colors.HOT_PINK),
                                                    Component.text("Search")))
                                            .material(Material.OAK_WALL_SIGN)
                                            .action(action -> {
                                                searchQuery = action.line(0);
                                                queryRef.value = searchQuery;
                                                schedule(action.player(), () -> buildGui(action.player()));
                                            })
                                            .open(player));
                                }));
                    });
                })
                .build()
                .open(p);
    }

    private GuiItem<Player, ItemStack> prevButton(Player p, PagerState<Material> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Previous").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Go to previous page", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                    state.prev();
                });
    }

    private GuiItem<Player, ItemStack> nextButton(Player p, PagerState<Material> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Next").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Go to next page", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                    state.next();
                });
    }

    private void openEnchantmentGui(Player p, ItemStack item, List<Enchantment> enchants, GuiView parentView) {
        final var navigating = new boolean[]{false};

        Gui.of(3)
                .title(Text.toSmallCapsComponent("Enchantments Editor"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0]) schedule(p, () -> buildGui(p));
                })
                .statelessComponent(con -> {
                    con.setItem(2, 5, ItemBuilder.from(item).asGuiItem());

                    con.setItem(2, 2, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Cancel").color(Colors.RED).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to cancel").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> buildGui(player));
                            }));

                    con.setItem(2, 4, ItemBuilder.from(Material.BOOK)
                            .name(Text.toSmallCapsComponent("Clear Enchantments").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to clear all enchantments").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                ItemStack cleared = item.clone();
                                for (Enchantment e : new ArrayList<>(cleared.getEnchantments().keySet())) cleared.removeEnchantment(e);
                                schedule(player, () -> openEnchantmentGui(player, cleared, enchants, ctx.guiView()));
                            }));

                    con.setItem(2, 6, ItemBuilder.from(Material.ENCHANTED_BOOK)
                            .name(Text.toSmallCapsComponent("Pick Enchantments").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to choose an enchantment").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> openPickEnchantmentsGui(player, item, item.clone(), enchants, 0, ctx.guiView()));
                            }));

                    con.setItem(2, 8, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Confirm").color(TextColor.color(0x00ff00)).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to confirm enchantments").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                processPick(player, item);
                            }));
                })
                .build()
                .open(p);
    }

    private void openPickEnchantmentsGui(Player p, ItemStack original, ItemStack current, List<Enchantment> enchants, int scrollIndex, GuiView enchantView) {
        final var navigating = new boolean[]{false};

        Gui.of(3)
                .title(Text.toSmallCapsComponent("Pick Enchantments"))
                .spamPreventionDuration(110)
                .onClose(() -> {
                    if (!navigating[0]) schedule(p, () -> openEnchantmentGui(p, original, enchants, enchantView));
                })
                .statelessComponent(con -> {
                    for (int i = 0; i < 9; i++) {
                        int col = i + 1;
                        boolean edge = col == 1 || col == 9;

                        con.setItem(1, col, ItemBuilder.from(edge ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                                .name(Component.empty()).asGuiItem());
                        con.setItem(3, col, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                                .name(Component.empty()).asGuiItem());

                        if (!edge) {
                            int idx = ((scrollIndex + (col - 2)) % enchants.size() + enchants.size()) % enchants.size();
                            Enchantment ench = enchants.get(idx);
                            int curLevel = current.getEnchantmentLevel(ench);
                            int maxLevel = ench.getMaxLevel();
                            boolean active = current.containsEnchantment(ench);

                            String loreLine = curLevel == 0 ? "(Click To Add)" : curLevel < maxLevel ? "(Click To Upgrade)" : "(Click To Remove)";

                            con.setItem(2, col, ItemBuilder.from(Material.ENCHANTED_BOOK)
                                    .name((active
                                            ? Text.toSmallCapsComponent("Active").color(Colors.GREEN)
                                            : Text.toSmallCapsComponent("Inactive").color(Colors.RED))
                                            .decoration(TextDecoration.ITALIC, false))
                                    .lore(Component.text(loreLine).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                                    .enchant(ench, Math.max(1, curLevel))
                                    .flags(ItemFlag.HIDE_STORED_ENCHANTS)
                                    .asGuiItem((player, ctx) -> {
                                        navigating[0] = true;
                                        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                                        ItemStack next = current.clone();
                                        if (active && curLevel >= maxLevel) {
                                            next.removeEnchantment(ench);
                                        } else {
                                            for (Enchantment ex : new ArrayList<>(next.getEnchantments().keySet())) {
                                                if (ench.conflictsWith(ex)) next.removeEnchantment(ex);
                                            }
                                            next.addUnsafeEnchantment(ench, curLevel + 1);
                                        }
                                        schedule(player, () -> openPickEnchantmentsGui(player, original, next, enchants, scrollIndex, enchantView));
                                    }));
                        }
                    }

                    con.setItem(2, 1, ItemBuilder.from(Material.ARROW)
                            .name(Text.toSmallCapsComponent("Previous").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Scroll enchantments").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                                ItemStack snap = current.clone();
                                schedule(player, () -> openPickEnchantmentsGui(player, original, snap, enchants, (scrollIndex - 1 + enchants.size()) % enchants.size(), enchantView));
                            }));

                    con.setItem(2, 9, ItemBuilder.from(Material.ARROW)
                            .name(Text.toSmallCapsComponent("Next").color(Colors.HOT_PINK).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Scroll enchantments").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                                ItemStack snap = current.clone();
                                schedule(player, () -> openPickEnchantmentsGui(player, original, snap, enchants, (scrollIndex + 1) % enchants.size(), enchantView));
                            }));

                    con.setItem(3, 1, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Cancel").color(Colors.RED).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to cancel enchantment changes").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                schedule(player, () -> openEnchantmentGui(player, original, enchants, enchantView));
                            }));

                    con.setItem(3, 9, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                            .name(Text.toSmallCapsComponent("Confirm").color(TextColor.color(0x00ff00)).decoration(TextDecoration.ITALIC, false))
                            .lore(Component.text("Click to update enchantments").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                            .asGuiItem((player, ctx) -> {
                                navigating[0] = true;
                                player.playSound(player, Sound.UI_BUTTON_CLICK, 1f, 1f);
                                ItemStack snap = current.clone();
                                schedule(player, () -> openEnchantmentGui(player, snap, enchants, enchantView));
                            }));
                })
                .build()
                .open(p);
    }

    private void processPick(Player player, ItemStack item) {
        player.closeInventory();
        if (callback != null) callback.accept(new PickAction(this, player, item));
    }

    public record PickAction(ItemPicker picker, Player player, ItemStack item) {}
}