package cx.arcane.managers.commandManager.commands;

import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.playerManager.PlayerMeta;
import cx.arcane.utils.Colors;
import cx.arcane.utils.PlayerUtils;
import cx.arcane.utils.Text;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.triumphteam.gui.container.GuiContainer;
import dev.triumphteam.gui.element.GuiItem;
import dev.triumphteam.gui.layout.GuiLayout;
import dev.triumphteam.gui.paper.Gui;
import dev.triumphteam.gui.paper.builder.item.ItemBuilder;
import dev.triumphteam.gui.slot.Slot;
import dev.triumphteam.gui.state.pagination.PagerState;
import github.nighter.smartspawner.api.SmartSpawnerProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;
import org.jline.utils.Log;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.logging.log4j.core.appender.db.ColumnMapping.toKey;

@Command(name = "shop")
public class ShopCommand {

    public record ShopEntry(ItemStack item, long price) {
        public ShopEntry(Material material, long price) {
            this(ItemStack.of(material), price);
        }

        public ItemStack create() {
            return item.clone();
        }
    }

    private record Shop(String title, Material navMaterial, TextColor navColor, List<ShopEntry> items) {}

    private static ItemStack potion(PotionType type) {
        ItemStack item = ItemStack.of(Material.POTION);
        item.editMeta(meta -> {
            if (meta instanceof org.bukkit.inventory.meta.PotionMeta pm)
                pm.setBasePotionType(type);
        });
        return item;
    }

    private static ItemStack splashPotion(PotionType type) {
        ItemStack item = ItemStack.of(Material.SPLASH_POTION);
        item.editMeta(meta -> {
            if (meta instanceof org.bukkit.inventory.meta.PotionMeta pm)
                pm.setBasePotionType(type);
        });
        return item;
    }

    private static ItemStack lingeringPotion(PotionType type) {
        ItemStack item = ItemStack.of(Material.LINGERING_POTION);
        item.editMeta(meta -> {
            if (meta instanceof org.bukkit.inventory.meta.PotionMeta pm)
                pm.setBasePotionType(type);
        });
        return item;
    }

    private static ItemStack arrow(PotionType type) {
        ItemStack item = ItemStack.of(Material.TIPPED_ARROW);
        item.editMeta(meta -> {
            if (meta instanceof org.bukkit.inventory.meta.PotionMeta pm)
                pm.setBasePotionType(type);
        });
        return item;
    }

    private static ItemStack firework(int duration) {
        ItemStack item = ItemStack.of(Material.FIREWORK_ROCKET);
        item.editMeta(meta -> {
            if (meta instanceof org.bukkit.inventory.meta.FireworkMeta pm)
                pm.setPower(duration);
        });
        return item;
    }

    private static ItemStack spawner(EntityType type) {
        ItemStack spawner = SmartSpawnerProvider.getAPI().createSpawnerItem(type, 1);
        return spawner;
    }

    private static final Shop WORLD;
    private static final Shop NETHER;
    private static final Shop END;
    private static final Shop GEAR;
    private static final Shop REDSTONE;
    private static final Shop FARMING;
    private static final Shop SPAWNERS;
    private static final List<Shop> SHOPS;

    static {
        WORLD = new Shop("World", Material.GRASS_BLOCK, NamedTextColor.GREEN, List.of(
                new ShopEntry(Material.GRASS_BLOCK, 10),
                new ShopEntry(Material.PODZOL, 10),
                new ShopEntry(Material.MYCELIUM, 10),
                new ShopEntry(Material.DIRT_PATH, 6),
                new ShopEntry(Material.DIRT, 5),
                new ShopEntry(Material.COARSE_DIRT, 8),
                new ShopEntry(Material.ROOTED_DIRT, 12),

                new ShopEntry(Material.STONE, 15),
                new ShopEntry(Material.SMOOTH_STONE, 20),
                new ShopEntry(Material.STONE_BRICKS, 25),
                new ShopEntry(Material.SAND, 15),
                new ShopEntry(Material.SANDSTONE, 20),
                new ShopEntry(Material.RED_SAND, 15),
                new ShopEntry(Material.RED_SANDSTONE, 20),

                new ShopEntry(Material.MUD, 25),
                new ShopEntry(Material.CLAY, 45),
                new ShopEntry(Material.GRAVEL, 15),
                new ShopEntry(Material.ICE, 50),
                new ShopEntry(Material.PACKED_ICE, 80),
                new ShopEntry(Material.BLUE_ICE, 120),
                new ShopEntry(Material.CALCITE, 20),

                new ShopEntry(Material.SNOW_BLOCK, 60),
                new ShopEntry(Material.SNOW, 15),
                new ShopEntry(Material.MOSS_BLOCK, 50),
                new ShopEntry(Material.MOSS_CARPET, 15),
                new ShopEntry(Material.DRIPSTONE_BLOCK, 150),
                new ShopEntry(Material.POINTED_DRIPSTONE, 150),
                new ShopEntry(Material.TUFF, 15),

                new ShopEntry(Material.SEA_LANTERN, 250),
                new ShopEntry(Material.PEARLESCENT_FROGLIGHT, 150),
                new ShopEntry(Material.OCHRE_FROGLIGHT, 150),
                new ShopEntry(Material.VERDANT_FROGLIGHT, 150),

                new ShopEntry(Material.DEEPSLATE, 25),
                new ShopEntry(Material.DEEPSLATE_BRICKS, 30),
                new ShopEntry(Material.DEEPSLATE_TILES, 35),

                new ShopEntry(Material.OAK_LOG, 50),
                new ShopEntry(Material.SPRUCE_LOG, 50),
                new ShopEntry(Material.BIRCH_LOG, 50),
                new ShopEntry(Material.JUNGLE_LOG, 50),
                new ShopEntry(Material.ACACIA_LOG, 50),
                new ShopEntry(Material.DARK_OAK_LOG, 50),
                new ShopEntry(Material.MANGROVE_LOG, 50),
                new ShopEntry(Material.CHERRY_LOG, 50),
                new ShopEntry(Material.PALE_OAK_LOG, 50),

                new ShopEntry(Material.CHEST, 150),
                new ShopEntry(Material.BOOKSHELF, 250),
                new ShopEntry(Material.CHISELED_BOOKSHELF, 150),
                new ShopEntry(Material.SCAFFOLDING, 50),
                new ShopEntry(Material.LADDER, 15),

                new ShopEntry(Material.OAK_SIGN, 25),
                new ShopEntry(Material.SPRUCE_SIGN, 25),
                new ShopEntry(Material.BIRCH_SIGN, 25),
                new ShopEntry(Material.JUNGLE_SIGN, 25),
                new ShopEntry(Material.ACACIA_SIGN, 25),
                new ShopEntry(Material.DARK_OAK_SIGN, 25),
                new ShopEntry(Material.MANGROVE_SIGN, 25),
                new ShopEntry(Material.CHERRY_SIGN, 25),
                new ShopEntry(Material.PALE_OAK_SIGN, 25),

                new ShopEntry(Material.MUSHROOM_STEM, 5),
                new ShopEntry(Material.OAK_LEAVES, 5),
                new ShopEntry(Material.SPRUCE_LEAVES, 5),
                new ShopEntry(Material.BIRCH_LEAVES, 5),
                new ShopEntry(Material.JUNGLE_LEAVES, 6),
                new ShopEntry(Material.ACACIA_LEAVES, 6),
                new ShopEntry(Material.DARK_OAK_LEAVES, 6),
                new ShopEntry(Material.MANGROVE_LEAVES, 6),
                new ShopEntry(Material.CHERRY_LEAVES, 8),

                new ShopEntry(Material.OAK_SAPLING, 120),
                new ShopEntry(Material.SPRUCE_SAPLING, 120),
                new ShopEntry(Material.BIRCH_SAPLING, 120),
                new ShopEntry(Material.JUNGLE_SAPLING, 120),
                new ShopEntry(Material.ACACIA_SAPLING, 120),
                new ShopEntry(Material.DARK_OAK_SAPLING, 120),
                new ShopEntry(Material.CHERRY_SAPLING, 120),
                new ShopEntry(Material.PALE_OAK_SAPLING, 120),

                new ShopEntry(Material.BROWN_MUSHROOM_BLOCK, 120),
                new ShopEntry(Material.RED_MUSHROOM_BLOCK, 120),
                new ShopEntry(Material.PALE_OAK_LEAVES, 120),
                new ShopEntry(Material.AZALEA_LEAVES, 120),
                new ShopEntry(Material.FLOWERING_AZALEA_LEAVES, 120),
                new ShopEntry(Material.AZALEA, 120),
                new ShopEntry(Material.FLOWERING_AZALEA, 120),

                new ShopEntry(Material.TUBE_CORAL_BLOCK, 850),
                new ShopEntry(Material.BRAIN_CORAL_BLOCK, 850),
                new ShopEntry(Material.BUBBLE_CORAL_BLOCK, 850),
                new ShopEntry(Material.FIRE_CORAL_BLOCK, 850),
                new ShopEntry(Material.HORN_CORAL_BLOCK, 850),

                new ShopEntry(Material.TUBE_CORAL, 50),
                new ShopEntry(Material.BRAIN_CORAL, 50),
                new ShopEntry(Material.BUBBLE_CORAL, 50),
                new ShopEntry(Material.FIRE_CORAL, 50),
                new ShopEntry(Material.HORN_CORAL, 50),

                new ShopEntry(Material.WHITE_WOOL, 80),
                new ShopEntry(Material.LIGHT_GRAY_WOOL, 80),
                new ShopEntry(Material.GRAY_WOOL, 80),
                new ShopEntry(Material.BLACK_WOOL, 80),
                new ShopEntry(Material.BROWN_WOOL, 80),
                new ShopEntry(Material.RED_WOOL, 80),
                new ShopEntry(Material.ORANGE_WOOL, 80),
                new ShopEntry(Material.YELLOW_WOOL, 80),
                new ShopEntry(Material.LIME_WOOL, 80),
                new ShopEntry(Material.GREEN_WOOL, 80),
                new ShopEntry(Material.CYAN_WOOL, 80),
                new ShopEntry(Material.LIGHT_BLUE_WOOL, 80),
                new ShopEntry(Material.BLUE_WOOL, 80),
                new ShopEntry(Material.PURPLE_WOOL, 80),
                new ShopEntry(Material.MAGENTA_WOOL, 80),
                new ShopEntry(Material.PINK_WOOL, 80),

                new ShopEntry(Material.TERRACOTTA, 65),
                new ShopEntry(Material.WHITE_TERRACOTTA, 65),
                new ShopEntry(Material.LIGHT_GRAY_TERRACOTTA, 65),
                new ShopEntry(Material.GRAY_TERRACOTTA, 65),
                new ShopEntry(Material.BLACK_TERRACOTTA, 65),
                new ShopEntry(Material.BROWN_TERRACOTTA, 65),
                new ShopEntry(Material.RED_TERRACOTTA, 65),
                new ShopEntry(Material.ORANGE_TERRACOTTA, 65),
                new ShopEntry(Material.YELLOW_TERRACOTTA, 65),
                new ShopEntry(Material.LIME_TERRACOTTA, 65),
                new ShopEntry(Material.GREEN_TERRACOTTA, 65),
                new ShopEntry(Material.CYAN_TERRACOTTA, 65),
                new ShopEntry(Material.LIGHT_BLUE_TERRACOTTA, 65),
                new ShopEntry(Material.BLUE_TERRACOTTA, 65),
                new ShopEntry(Material.PURPLE_TERRACOTTA, 65),
                new ShopEntry(Material.MAGENTA_TERRACOTTA, 65),
                new ShopEntry(Material.PINK_TERRACOTTA, 65),

                new ShopEntry(Material.WHITE_CONCRETE_POWDER, 120),
                new ShopEntry(Material.LIGHT_GRAY_CONCRETE_POWDER, 120),
                new ShopEntry(Material.GRAY_CONCRETE_POWDER, 120),
                new ShopEntry(Material.BLACK_CONCRETE_POWDER, 120),
                new ShopEntry(Material.BROWN_CONCRETE_POWDER, 120),
                new ShopEntry(Material.RED_CONCRETE_POWDER, 120),
                new ShopEntry(Material.ORANGE_CONCRETE_POWDER, 120),
                new ShopEntry(Material.YELLOW_CONCRETE_POWDER, 120),
                new ShopEntry(Material.LIME_CONCRETE_POWDER, 120),
                new ShopEntry(Material.GREEN_CONCRETE_POWDER, 120),
                new ShopEntry(Material.CYAN_CONCRETE_POWDER, 120),
                new ShopEntry(Material.LIGHT_BLUE_CONCRETE_POWDER, 120),
                new ShopEntry(Material.BLUE_CONCRETE_POWDER, 120),
                new ShopEntry(Material.PURPLE_CONCRETE_POWDER, 120),
                new ShopEntry(Material.MAGENTA_CONCRETE_POWDER, 120),
                new ShopEntry(Material.PINK_CONCRETE_POWDER, 120),

                new ShopEntry(Material.GLASS, 65),
                new ShopEntry(Material.TINTED_GLASS, 120),
                new ShopEntry(Material.WHITE_STAINED_GLASS, 150),
                new ShopEntry(Material.LIGHT_GRAY_STAINED_GLASS, 150),
                new ShopEntry(Material.GRAY_STAINED_GLASS, 150),
                new ShopEntry(Material.BLACK_STAINED_GLASS, 150),
                new ShopEntry(Material.BROWN_STAINED_GLASS, 150),
                new ShopEntry(Material.RED_STAINED_GLASS, 150),
                new ShopEntry(Material.ORANGE_STAINED_GLASS, 150),
                new ShopEntry(Material.YELLOW_STAINED_GLASS, 150),
                new ShopEntry(Material.LIME_STAINED_GLASS, 150),
                new ShopEntry(Material.GREEN_STAINED_GLASS, 150),
                new ShopEntry(Material.CYAN_STAINED_GLASS, 150),
                new ShopEntry(Material.LIGHT_BLUE_STAINED_GLASS, 150),
                new ShopEntry(Material.BLUE_STAINED_GLASS, 150),
                new ShopEntry(Material.PURPLE_STAINED_GLASS, 150),
                new ShopEntry(Material.MAGENTA_STAINED_GLASS, 150),
                new ShopEntry(Material.PINK_STAINED_GLASS, 150),

                new ShopEntry(Material.WHITE_DYE, 35),
                new ShopEntry(Material.LIGHT_GRAY_DYE, 35),
                new ShopEntry(Material.GRAY_DYE, 35),
                new ShopEntry(Material.BLACK_DYE, 35),
                new ShopEntry(Material.BROWN_DYE, 35),
                new ShopEntry(Material.RED_DYE, 35),
                new ShopEntry(Material.ORANGE_DYE, 35),
                new ShopEntry(Material.YELLOW_DYE, 35),
                new ShopEntry(Material.LIME_DYE, 35),
                new ShopEntry(Material.GREEN_DYE, 35),
                new ShopEntry(Material.CYAN_DYE, 35),
                new ShopEntry(Material.LIGHT_BLUE_DYE, 35),
                new ShopEntry(Material.BLUE_DYE, 35),
                new ShopEntry(Material.PURPLE_DYE, 35),
                new ShopEntry(Material.MAGENTA_DYE, 35),
                new ShopEntry(Material.PINK_DYE, 35),

                new ShopEntry(Material.WHITE_CANDLE, 20),
                new ShopEntry(Material.LIGHT_GRAY_CANDLE, 20),
                new ShopEntry(Material.GRAY_CANDLE, 20),
                new ShopEntry(Material.BLACK_CANDLE, 20),
                new ShopEntry(Material.BROWN_CANDLE, 20),
                new ShopEntry(Material.RED_CANDLE, 20),
                new ShopEntry(Material.ORANGE_CANDLE, 20),
                new ShopEntry(Material.YELLOW_CANDLE, 20),
                new ShopEntry(Material.LIME_CANDLE, 20),
                new ShopEntry(Material.GREEN_CANDLE, 20),
                new ShopEntry(Material.CYAN_CANDLE, 20),
                new ShopEntry(Material.LIGHT_BLUE_CANDLE, 20),
                new ShopEntry(Material.BLUE_CANDLE, 20),
                new ShopEntry(Material.PURPLE_CANDLE, 20),
                new ShopEntry(Material.MAGENTA_CANDLE, 20),
                new ShopEntry(Material.PINK_CANDLE, 20),

                new ShopEntry(Material.WHITE_BANNER, 450),
                new ShopEntry(Material.LIGHT_GRAY_BANNER, 450),
                new ShopEntry(Material.GRAY_BANNER, 450),
                new ShopEntry(Material.BLACK_BANNER, 450),
                new ShopEntry(Material.BROWN_BANNER, 450),
                new ShopEntry(Material.RED_BANNER, 450),
                new ShopEntry(Material.ORANGE_BANNER, 450),
                new ShopEntry(Material.YELLOW_BANNER, 450),
                new ShopEntry(Material.LIME_BANNER, 450),
                new ShopEntry(Material.GREEN_BANNER, 450),
                new ShopEntry(Material.CYAN_BANNER, 450),
                new ShopEntry(Material.LIGHT_BLUE_BANNER, 450),
                new ShopEntry(Material.BLUE_BANNER, 450),
                new ShopEntry(Material.PURPLE_BANNER, 450),
                new ShopEntry(Material.MAGENTA_BANNER, 450),
                new ShopEntry(Material.PINK_BANNER, 450)
        ));

        NETHER = new Shop("Nether", Material.NETHERRACK, TextColor.color(0xff0000), List.of(
                new ShopEntry(Material.NETHERRACK, 50),
                new ShopEntry(Material.MAGMA_BLOCK, 25),
                new ShopEntry(Material.SOUL_SAND, 350),
                new ShopEntry(Material.SOUL_SOIL, 15),
                new ShopEntry(Material.NETHER_BRICKS, 50),
                new ShopEntry(Material.RED_NETHER_BRICKS, 35),
                new ShopEntry(Material.CRYING_OBSIDIAN, 250),
                new ShopEntry(Material.BLAZE_ROD, 250),
                new ShopEntry(Material.MAGMA_CREAM, 450),
                new ShopEntry(Material.NETHER_WART, 450),
                new ShopEntry(Material.GLOWSTONE_DUST, 50),
                new ShopEntry(Material.GHAST_TEAR, 1000),
                new ShopEntry(Material.QUARTZ, 30),
                new ShopEntry(Material.QUARTZ_BLOCK, 300)
        ));

        END = new Shop("End", Material.END_STONE, TextColor.color(0xF1FF85), List.of(
                new ShopEntry(Material.END_STONE, 150),
                new ShopEntry(Material.ENDER_CHEST, 1500),
                new ShopEntry(Material.ENDER_PEARL, 80),
                new ShopEntry(Material.DRAGON_BREATH, 2500),
                new ShopEntry(Material.CHORUS_FRUIT, 850),
                new ShopEntry(Material.SHULKER_SHELL, 500),
                new ShopEntry(Material.SHULKER_BOX, 2000),

                new ShopEntry(Material.END_STONE_BRICKS, 350),
                new ShopEntry(Material.PURPUR_BLOCK, 550),
                new ShopEntry(Material.WHITE_BED, 650),
                new ShopEntry(Material.END_ROD, 520),
                new ShopEntry(Material.POPPED_CHORUS_FRUIT, 250),
                new ShopEntry(Material.CHORUS_FLOWER, 150),
                new ShopEntry(Material.CHORUS_PLANT, 200)
        ));

        GEAR = new Shop("Gear", Material.TOTEM_OF_UNDYING, TextColor.color(0xFFA700), List.of(
                new ShopEntry(Material.RESPAWN_ANCHOR, 800),
                new ShopEntry(Material.GLOWSTONE, 120),
                new ShopEntry(Material.OBSIDIAN, 50),
                new ShopEntry(Material.END_CRYSTAL, 100),
                new ShopEntry(Material.TOTEM_OF_UNDYING, 1000),
                new ShopEntry(Material.GOLDEN_APPLE, 350),
                new ShopEntry(Material.EXPERIENCE_BOTTLE, 100),

                new ShopEntry(Material.ENDER_PEARL, 80),
                new ShopEntry(firework(1), 85),
                new ShopEntry(Material.WIND_CHARGE, 150),
                new ShopEntry(Material.TNT_MINECART, 300),
                new ShopEntry(Material.TNT, 200),
                new ShopEntry(Material.FLINT_AND_STEEL, 75),
                new ShopEntry(Material.FISHING_ROD, 75),

                new ShopEntry(Material.ARROW, 15),
                new ShopEntry(Material.SHIELD, 100),
                new ShopEntry(Material.RAIL, 25),
                new ShopEntry(Material.COBWEB, 85),
                new ShopEntry(Material.MILK_BUCKET, 120),
                new ShopEntry(Material.POWDER_SNOW_BUCKET, 80),
                new ShopEntry(Material.LAVA_BUCKET, 650),

                new ShopEntry(Material.BOW, 350),
                new ShopEntry(Material.CROSSBOW, 550),
                new ShopEntry(Material.GOLDEN_CARROT, 220),
                new ShopEntry(Material.IRON_SWORD, 200),
                new ShopEntry(Material.IRON_AXE, 200),
                new ShopEntry(Material.CHORUS_FRUIT, 850),
                new ShopEntry(Material.ENDER_CHEST, 1500),

                new ShopEntry(potion(PotionType.STRENGTH), 500),
                new ShopEntry(potion(PotionType.SWIFTNESS), 500),
                new ShopEntry(potion(PotionType.FIRE_RESISTANCE), 500),
                new ShopEntry(potion(PotionType.INVISIBILITY), 500),
                new ShopEntry(potion(PotionType.SLOW_FALLING), 500),
                new ShopEntry(potion(PotionType.WEAKNESS), 500),
                new ShopEntry(potion(PotionType.HARMING), 500),

                new ShopEntry(splashPotion(PotionType.STRENGTH), 350),
                new ShopEntry(splashPotion(PotionType.SWIFTNESS), 350),
                new ShopEntry(splashPotion(PotionType.FIRE_RESISTANCE), 350),
                new ShopEntry(splashPotion(PotionType.INVISIBILITY), 350),
                new ShopEntry(splashPotion(PotionType.SLOW_FALLING), 350),
                new ShopEntry(splashPotion(PotionType.WEAKNESS), 350),
                new ShopEntry(splashPotion(PotionType.HARMING), 350),

                new ShopEntry(lingeringPotion(PotionType.STRENGTH), 250),
                new ShopEntry(lingeringPotion(PotionType.SWIFTNESS), 250),
                new ShopEntry(lingeringPotion(PotionType.FIRE_RESISTANCE), 250),
                new ShopEntry(lingeringPotion(PotionType.INVISIBILITY), 250),
                new ShopEntry(lingeringPotion(PotionType.SLOW_FALLING), 250),
                new ShopEntry(lingeringPotion(PotionType.WEAKNESS), 250),
                new ShopEntry(lingeringPotion(PotionType.HARMING), 250),

                new ShopEntry(arrow(PotionType.STRENGTH), 50),
                new ShopEntry(arrow(PotionType.SWIFTNESS), 50),
                new ShopEntry(arrow(PotionType.FIRE_RESISTANCE), 50),
                new ShopEntry(arrow(PotionType.INVISIBILITY), 50),
                new ShopEntry(arrow(PotionType.SLOW_FALLING), 50),
                new ShopEntry(arrow(PotionType.WEAKNESS), 50),
                new ShopEntry(arrow(PotionType.HARMING), 50)
        ));

        REDSTONE = new Shop("Redstone", Material.REDSTONE, TextColor.color(0xFF0000), List.of(
                new ShopEntry(Material.DISPENSER, 500),
                new ShopEntry(Material.DROPPER, 500),
                new ShopEntry(Material.OBSERVER, 500),
                new ShopEntry(Material.STICKY_PISTON, 800),
                new ShopEntry(Material.PISTON, 600),
                new ShopEntry(Material.FURNACE, 500),
                new ShopEntry(Material.CRAFTER, 500),

                new ShopEntry(Material.REDSTONE_BLOCK, 550),
                new ShopEntry(Material.REDSTONE, 55),
                new ShopEntry(Material.REPEATER, 100),
                new ShopEntry(Material.COMPARATOR, 150),
                new ShopEntry(Material.REDSTONE_TORCH, 65),
                new ShopEntry(Material.LEVER, 25),
                new ShopEntry(Material.TRIPWIRE_HOOK, 125),

                new ShopEntry(Material.REDSTONE_LAMP, 50),
                new ShopEntry(Material.NOTE_BLOCK, 50),
                new ShopEntry(Material.SLIME_BLOCK, 600),
                new ShopEntry(Material.HONEY_BLOCK, 800),
                new ShopEntry(Material.TARGET, 50),
                new ShopEntry(Material.COPPER_BULB, 50),
                new ShopEntry(Material.TRAPPED_CHEST, 200),

                new ShopEntry(Material.HOPPER, 500),
                new ShopEntry(Material.MINECART, 450),
                new ShopEntry(Material.HOPPER_MINECART, 650),
                new ShopEntry(Material.RAIL, 25),
                new ShopEntry(Material.POWERED_RAIL, 350),
                new ShopEntry(Material.ACTIVATOR_RAIL, 120),
                new ShopEntry(Material.DETECTOR_RAIL, 120)
        ));

        FARMING = new Shop("Farming", Material.SUGAR_CANE, TextColor.color(0x00FF00), List.of(

                new ShopEntry(Material.MELON_SLICE, 80),
                new ShopEntry(Material.POTATO, 55),
                new ShopEntry(Material.CARROT, 65),
                new ShopEntry(Material.COOKED_BEEF, 100),
                new ShopEntry(Material.COOKED_MUTTON, 150),
                new ShopEntry(Material.COOKED_CHICKEN, 200),
                new ShopEntry(Material.COD, 25),

                new ShopEntry(Material.SWEET_BERRIES, 30),
                new ShopEntry(Material.BAKED_POTATO, 65),
                new ShopEntry(Material.GOLDEN_CARROT, 220),
                new ShopEntry(Material.BEETROOT, 150),
                new ShopEntry(Material.COOKIE, 150),
                new ShopEntry(Material.PUMPKIN_PIE, 150),
                new ShopEntry(Material.CAKE, 500),


                new ShopEntry(Material.APPLE, 150),
                new ShopEntry(Material.WHEAT, 250),
                new ShopEntry(Material.GLOW_BERRIES, 350),
                new ShopEntry(Material.NETHER_WART, 450),
                new ShopEntry(Material.KELP, 500),
                new ShopEntry(Material.SALMON, 150),
                new ShopEntry(Material.SEA_PICKLE, 500),

                new ShopEntry(Material.WHEAT_SEEDS, 65),
                new ShopEntry(Material.COCOA_BEANS, 150),
                new ShopEntry(Material.PUMPKIN_SEEDS, 150),
                new ShopEntry(Material.MELON_SEEDS, 150),
                new ShopEntry(Material.BEETROOT_SEEDS, 150),
                new ShopEntry(Material.BAMBOO, 500),
                new ShopEntry(Material.SUGAR_CANE, 500)
        ));

        SPAWNERS = new Shop("Spawners", Material.SPAWNER, Colors.HOT_PINK, List.of(
                new ShopEntry(spawner(EntityType.PIG), 50_000),
                new ShopEntry(spawner(EntityType.COW), 100_000),
                new ShopEntry(spawner(EntityType.SHEEP), 100_000),
                new ShopEntry(spawner(EntityType.SQUID), 250_000),
                new ShopEntry(spawner(EntityType.PILLAGER), 300_000),
                new ShopEntry(spawner(EntityType.BLAZE), 450_000),
                new ShopEntry(spawner(EntityType.SKELETON), 550_000),

                new ShopEntry(spawner(EntityType.ZOMBIE), 650_000),
                new ShopEntry(spawner(EntityType.SLIME), 900_000),
                new ShopEntry(spawner(EntityType.SPIDER), 1_000_000),
                new ShopEntry(spawner(EntityType.WITCH), 2_000_000),
                new ShopEntry(spawner(EntityType.ENDERMAN), 2_500_000),
                new ShopEntry(spawner(EntityType.CREEPER), 2_500_000),
                new ShopEntry(spawner(EntityType.IRON_GOLEM), 3_500_000)
        ));

        SHOPS = List.of(WORLD, NETHER, END, GEAR, REDSTONE, FARMING, SPAWNERS);
    }

    public static List<ShopEntry> getAllShopEntries() {
        return SHOPS.stream()
                .flatMap(shop -> shop.items().stream())
                .distinct()
                .toList();
    }

    @Execute
    public void execute(@Sender Player pPlayer) {
        pPlayer.playSound(pPlayer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1, 1);
        openMainShop(pPlayer);
    }

    private static void openMainShop(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);

        int rows = Math.min(Math.max((int) Math.ceil(SHOPS.size() / 7.0) + 2, 3), 6);

        Gui.of(rows)
                .title(Text.toSmallCapsComponent("Shop"))
                .spamPreventionDuration(110)
                .statelessComponent(con -> {
                    for (int i = 0; i < SHOPS.size(); i++) {
                        Shop shop = SHOPS.get(i);
                        int col = (i % 7) + 2;
                        int row = (i / 7) + 2;
                        con.setItem(row, col, shopNavItem(shop.navMaterial(), shop.title(), shop.navColor(),
                                () -> openShop(p, shop)));
                    }
                })
                .build()
                .open(p);
    }
    private static void openShop(Player p, Shop shop) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);

        final var navigating = new boolean[]{false};

        int maxContentRows = 4;
        int itemsPerPage = maxContentRows * 7;

        boolean paginated = shop.items().size() > itemsPerPage;

        int contentRows = paginated
                ? maxContentRows
                : (int) Math.ceil(shop.items().size() / 7.0);

        int rows = Math.min(Math.max(contentRows + 2, 3), 6);

        int totalPages = (shop.items().size() + itemsPerPage - 1) / itemsPerPage;

        Gui.of(rows)
                .title(Text.toSmallCapsComponent(shop.title() + " Shop"))
                .spamPreventionDuration(110)
                .component(component -> {

                    PagerState<ShopEntry> pageState = null;

                    if (paginated) {
                        pageState = PagerState.of(
                                shop.items(),
                                GuiLayout.box(Slot.of(2, 2), Slot.of(rows - 1, 8))
                        );
                        component.remember(pageState);
                    }

                    PagerState<ShopEntry> finalPageState = pageState;

                    component.render(con -> {

                        if (paginated) {

                            finalPageState.forEach(entry -> {
                                ShopEntry se = entry.element();
                                con.setItem(entry.slot(), createItem(p, se, shop, navigating));
                            });

                            if (finalPageState.getCurrentPage() > 1) {
                                con.setItem(rows, 1, prevButton(p, finalPageState));
                            }

                            if (finalPageState.getCurrentPage() < totalPages && totalPages > 1) {
                                con.setItem(rows, 9, nextButton(p, finalPageState));
                            }

                        } else {

                            int index = 0;
                            for (ShopEntry se : shop.items()) {
                                int col = (index % 7) + 2;
                                int row = (index / 7) + 2;

                                con.setItem(row, col, createItem(p, se, shop, navigating));
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
                                    openMainShop(p);
                                }));
                    });
                })
                .build()
                .open(p);
    }

    private static GuiItem<Player, ItemStack> createItem(Player p, ShopEntry se, Shop shop, boolean[] navigating) {
        String formattedPrice = NumberFormat.getInstance().format(se.price());

        return ItemBuilder.from(se.create())
                .lore(Component.text("Price: ", Colors.WHITE).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("$" + formattedPrice, TextColor.color(0xff0000))
                                .decoration(TextDecoration.ITALIC, false)))
                .asGuiItem((player, ctx) -> {
                    navigating[0] = true;
                    openPurchaseGui(p, se, () -> openShop(p, shop));
                });
    }

    private static GuiItem<Player, ItemStack> prevButton(Player p, PagerState<ShopEntry> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Previous")
                        .color(Colors.HOT_PINK)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to go to previous page", Colors.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                    state.prev();
                });
    }

    private static GuiItem<Player, ItemStack> nextButton(Player p, PagerState<ShopEntry> state) {
        return ItemBuilder.from(Material.ARROW)
                .name(Text.toSmallCapsComponent("Next")
                        .color(Colors.HOT_PINK)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Click to go to next page", Colors.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> {
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                    state.next();
                });
    }

    private static void openPurchaseGui(Player p, ShopEntry entry, Runnable onBack) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);

        ItemStack baseItem = entry.create();
        long pricePerItem = entry.price();
        int maxStack = baseItem.getMaxStackSize();
        final var navigating = new boolean[]{false};

        Gui.of(3)
                .title(Text.toSmallCapsComponent("Confirm Purchase"))
                .spamPreventionDuration(110)
                .component(component -> {
                    final var qty = component.remember(1);

                    component.render(con -> {

                        ItemStack display = baseItem.clone();
                        display.setAmount(qty.get());

                        con.setItem(2, 5, ItemBuilder.from(display)
                                .lore(
                                        Component.text("Quantity: ", Colors.WHITE).decoration(TextDecoration.ITALIC, false)
                                                .append(Component.text(qty.get(), TextColor.color(0xff0000)).decoration(TextDecoration.ITALIC, false)),
                                        Component.text("Price: ", Colors.WHITE).decoration(TextDecoration.ITALIC, false)
                                                .append(Component.text("$" + (pricePerItem * qty.get()), TextColor.color(0xff0000)).decoration(TextDecoration.ITALIC, false))
                                )
                                .asGuiItem());

                        con.setItem(3, 4, ItemBuilder.from(Material.RED_STAINED_GLASS)
                                .name(Text.toSmallCapsComponent("Return").color(TextColor.color(0xff0000)).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> { navigating[0] = true; onBack.run(); }));

                        con.setItem(3, 6, ItemBuilder.from(Material.LIME_STAINED_GLASS)
                                .name(Text.toSmallCapsComponent("Confirm").color(TextColor.color(0x00ff00)).decoration(TextDecoration.ITALIC, false))
                                .asGuiItem((player, ctx) -> { navigating[0] = true; buy(entry, qty.get(), p); }));

                        if (qty.get() < maxStack) {
                            con.setItem(2, 7, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                                    .name(Text.toSmallCapsComponent("+1").color(TextColor.color(0x00ff00)).decoration(TextDecoration.ITALIC, false))
                                    .asGuiItem((player, ctx) -> qty.update(q -> Math.min(q + 1, maxStack))));

                            con.setItem(2, 8, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                                    .name(Text.toSmallCapsComponent("+16").color(TextColor.color(0x00ff00)).decoration(TextDecoration.ITALIC, false))
                                    .asGuiItem((player, ctx) -> qty.update(q -> Math.min(q + 16, maxStack))));

                            con.setItem(2, 9, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                                    .name(Text.toSmallCapsComponent("+64").color(TextColor.color(0x00ff00)).decoration(TextDecoration.ITALIC, false))
                                    .asGuiItem((player, ctx) -> qty.update(q -> Math.min(q + 64, maxStack))));
                        }

                        if (qty.get() > 1) {
                            con.setItem(2, 3, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                                    .name(Text.toSmallCapsComponent("-1").color(TextColor.color(0xff0000)).decoration(TextDecoration.ITALIC, false))
                                    .asGuiItem((player, ctx) -> qty.update(q -> Math.max(q - 1, 1))));
                        }

                        if (qty.get() >= 16) {
                            con.setItem(2, 2, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                                    .name(Text.toSmallCapsComponent("-16").color(TextColor.color(0xff0000)).decoration(TextDecoration.ITALIC, false))
                                    .asGuiItem((player, ctx) -> qty.update(q -> Math.max(q - 16, 1))));
                        }

                        if (qty.get() >= 64) {
                            con.setItem(2, 1, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                                    .name(Text.toSmallCapsComponent("-64").color(TextColor.color(0xff0000)).decoration(TextDecoration.ITALIC, false))
                                    .asGuiItem((player, ctx) -> qty.update(q -> Math.max(q - 64, 1))));
                        }
                    });
                })
                .build()
                .open(p);
    }

    private static void buy(ShopEntry entry, long quantity, Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);

        long totalPrice = entry.price() * quantity;

        if (!EcoManager.takeMoney(p.getUniqueId(), totalPrice)) {
            p.sendActionBar(Component.text("You don't have enough money!").color(TextColor.color(0xff0000)));
            p.sendMessage(Component.text("You don't have enough money!").color(TextColor.color(0xff0000)));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }

        ItemStack itemCopy = entry.create();
        itemCopy.setAmount((int) quantity);

        PlayerMeta pMeta = PlayerManager.getByUniqueId(p.getUniqueId()).getMeta();
        pMeta.setTotalBought(pMeta.getTotalBought() + totalPrice);

        PlayerUtils.giveOrDrop(p, itemCopy);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
    }

    private static void fillBorder(GuiContainer<Player, ItemStack> con, int rows, Material material) {
        GuiItem<Player, ItemStack> border = ItemBuilder.from(material).name(Component.empty()).asGuiItem();
        for (int col = 1; col <= 9; col++) {
            con.setItem(1, col, border);
            con.setItem(rows, col, border);
        }
        for (int row = 2; row < rows; row++) {
            con.setItem(row, 1, border);
            con.setItem(row, 9, border);
        }
    }

    private static GuiItem<Player, ItemStack> shopNavItem(Material material, String title, TextColor color, Runnable onClick) {
        return ItemBuilder.from(material)
                .name(Text.toSmallCapsComponent(title).color(color).decoration(TextDecoration.ITALIC, false))
                .asGuiItem((player, ctx) -> onClick.run());
    }
}