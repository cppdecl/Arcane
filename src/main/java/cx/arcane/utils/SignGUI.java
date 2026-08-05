package cx.arcane.utils;

import cx.arcane.Arcane;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public class SignGUI {
    private static final String META_KEY = "ArcLib:SignGUI:CurrentGUI";
    private static SignGUIListener listener;

    private Material signMaterial = Material.OAK_WALL_SIGN;
    private List<TextComponent> defaultLines = List.of(
            Component.text(""),
            Component.text("↑↑↑↑↑↑↑↑").color(TextColor.color(0x00FC92)),
            Component.text("Input"),
            Component.text("")
    );

    private Location virtualLocation;
    private BlockData originalBlockData;
    private Consumer<SignGuiAction> actionHandler;

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /**
     * Constructs a new SignGUI instance.
     * Registers the SignGUIListener if not already registered.
     */
    public SignGUI() {

        if (listener == null) {
            listener = new SignGUIListener(Arcane.getPlugin());
        }
    }

    /**
     * Sets the material of the sign to be used in the GUI.
     *
     * @param material The Material representing the sign type (e.g., OAK_WALL_SIGN).
     * @return The current SignGUI instance for method chaining.
     */
    public SignGUI material(Material material) {
        this.signMaterial = material;
        // ArcLib.debug("SignGUI -> Material set to " + material);
        return this;
    }

    /**
     * Sets a specific default line for the sign.
     *
     * @param index The line index (0-3).
     * @param line  The TextComponent to set for the specified line.
     * @return The current SignGUI instance for method chaining.
     * @throws IllegalArgumentException if the index is out of bounds.
     */
    public SignGUI line(int index, TextComponent line) {
        if (index < 0 || index > 3) throw new IllegalArgumentException("Line index must be between 0 and 3");
        defaultLines.set(index, line);
        return this;
    }

    /**
     * Sets all default lines for the sign.
     * If fewer than 4 lines are provided, remaining lines will be empty.
     * If more than 4 lines are provided, excess lines will be ignored.
     *
     * @param lines A list of TextComponent representing the lines.
     * @return The current SignGUI instance for method chaining.
     */
    public SignGUI lines(List<TextComponent> lines) {
        this.defaultLines = lines;
        // ArcLib.debug("SignGUI -> Default lines set: " + lines);
        return this;
    }

    /**
    * Sets a custom lambda to handle the sign input result.
    *
    * @param consumer A Consumer that takes a SignGuiAction object.
    * @return The current SignGUI instance for method chaining.
    * */
    public SignGUI action(Consumer<SignGuiAction> consumer) {
        this.actionHandler = consumer;
        // ArcLib.debug("SignGUI -> Action handler registered");
        return this;
    }

    /**
     * Opens a Sign Input GUI for the specified player.
     * However, make sure to run this on the entity's region thread!
     *
     * @param player The player for whom to open the sign GUI.
     */
    public void open(Player player) {
        // ArcLib.debug("SignGUI -> Opening sign GUI for " + player.getName());

        player.removeMetadata(META_KEY, Arcane.getPlugin());
        player.setMetadata(META_KEY, new FixedMetadataValue(Arcane.getPlugin(), this));

        Location behindHead = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(-3));
        this.virtualLocation = behindHead.toBlockLocation();

        Block block = virtualLocation.getBlock();
        this.originalBlockData = block.getBlockData();

        // ArcLib.debug("SignGUI -> Virtual sign placed at " + virtualLocation);

        BlockData signData = Bukkit.createBlockData(signMaterial);
        BlockState fakeState = signData.createBlockState();
        Sign sign = (Sign) fakeState;

        for (int i = 0; i < defaultLines.size(); i++) {
            sign.getSide(Side.FRONT).line(i, defaultLines.get(i));
        }

        player.sendBlockChange(virtualLocation, signData);
        player.sendBlockUpdate(virtualLocation, (TileState) fakeState);

        player.openVirtualSign(virtualLocation, Side.FRONT);
        // ArcLib.debug("SignGUI -> Virtual sign GUI opened for " + player.getName());

    }

    /**
     * Handles the input received from the sign GUI.
     *
     * @param player   The player who submitted the sign input.
     * @param rawLines The raw lines entered by the player.
     */
    private void handleInput(Player player, List<Component> rawLines) {
        // ArcLib.debug("SignGUI -> Handling input for " + player.getName());

        player.removeMetadata(META_KEY, Arcane.getPlugin());

        List<TextComponent> textLines = rawLines.stream()
                .map(comp -> (comp instanceof TextComponent tc) ? tc : Component.text(PLAIN.serialize(comp)))
                .toList();

        // ArcLib.debug("SignGUI -> Player " + player.getName() + " entered lines: " + textLines);

        if (actionHandler != null) {
            // ArcLib.debug("SignGUI -> Executing action handler for " + player.getName());
            actionHandler.accept(new SignGuiAction(this, player, textLines));
        }

        cleanup(player);
    }

    /**
     * Handles cleanup if the player quits during the sign GUI.
     *
     * @param player The player who quit.
     */
    private void handleQuit(Player player) {
        if (!player.hasMetadata(META_KEY)) return;
        player.removeMetadata(META_KEY, Arcane.getPlugin());
        // ArcLib.debug("SignGUI -> " + player.getName() + " quit during sign GUI, cleaning up");
        cleanup(player);
    }

    /**
     * Cleans up the virtual sign and restores the original block state.
     *
     * @param player The player for whom to clean up the sign GUI.
     */
    private void cleanup(Player player) {
        // ArcLib.debug("SignGUI -> Cleaning up virtual sign for " + player.getName());
        if (virtualLocation != null) {
            player.sendBlockChange(virtualLocation, originalBlockData);
            // ArcLib.debug("SignGUI -> Virtual sign restored at " + virtualLocation);
        }
    }

    /**
     * Represents the result of a sign GUI action.
     *
     * @param gui    The SignGUI instance.
     * @param player The player who submitted the input.
     * @param lines  The lines entered by the player.
     */
    public record SignGuiAction(SignGUI gui, Player player, List<TextComponent> lines) {
        public String line(int index) {
            if (index < 0 || index >= lines.size()) return "";
            return PlainTextComponentSerializer.plainText().serialize(lines.get(index));
        }
    }

    /**
     * Listener class to handle sign change and player quit events.
     * Registered globally when the first SignGUI instance is created.
     */
    public static class SignGUIListener implements Listener {
        public SignGUIListener(Plugin plugin) {
            // ArcLib.debug("SignGUIListener -> Registering event listener");
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }

        @EventHandler
        public void onSignChange(UncheckedSignChangeEvent e) {
            Player player = e.getPlayer();
            if (!player.hasMetadata(META_KEY)) return;

            SignGUI gui = (SignGUI) player.getMetadata(META_KEY).getFirst().value();
            if (gui == null) return;

            // Check location validity
            if (!e.getEditedBlockPosition().equals(gui.virtualLocation.toBlock())) {
                // ArcLib.debug("SignGUI -> Player " + player.getName() +
                   //     " tried to edit unexpected sign location. Expected: " +
                    //    gui.virtualLocation + " Got: " + e.getEditedBlockPosition());

                player.removeMetadata(META_KEY, Arcane.getPlugin());
                gui.cleanup(player);
                return;
            }

            // ArcLib.debug("SignGUI -> Valid sign input received from " + player.getName());
            gui.handleInput(player, e.lines());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent e) {
            Player player = e.getPlayer();
            if (!player.hasMetadata(META_KEY)) return;

            SignGUI gui = (SignGUI) player.getMetadata(META_KEY).getFirst().value();
            if (gui != null) {
                // ArcLib.debug("SignGUI -> Player " + player.getName() + " quit during sign GUI");
                gui.handleQuit(player);
            }
        }
    }
}
