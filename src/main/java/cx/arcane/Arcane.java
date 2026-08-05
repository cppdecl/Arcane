package cx.arcane;

import cx.arcane.managers.antiXrayManager.AntiXrayManager;
import cx.arcane.managers.auctionManager.AuctionManager;
import cx.arcane.managers.authManager.AuthManager;
import cx.arcane.managers.bountyManager.BountyManager;
import cx.arcane.managers.chatManager.ChatManager;
import cx.arcane.managers.clanManager.ClanManager;
import cx.arcane.managers.coinflipManager.CoinFlipManager;
import cx.arcane.managers.commandManager.CommandManager;
import cx.arcane.managers.crateManager.CrateManager;
import cx.arcane.managers.dbManager.DBManager;
import cx.arcane.managers.dependencyManager.DependencyManager;
import cx.arcane.managers.discordManager.DiscordManager;
import cx.arcane.managers.ecoManager.EcoManager;
import cx.arcane.managers.geoManager.GeoManager;
import cx.arcane.managers.gizmoManager.GizmoManager;
import cx.arcane.managers.homeManager.HomeManager;
import cx.arcane.managers.interactionManager.InteractionManager;
import cx.arcane.managers.itemManager.ItemManager;
import cx.arcane.managers.loreManager.LoreManager;
import cx.arcane.managers.motdManager.MOTDManager;
import cx.arcane.managers.ordersManager.OrdersManager;
import cx.arcane.managers.permissionManager.PermissionManager;
import cx.arcane.managers.placeholderManager.PlaceholderManager;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.managers.pluginManager.PluginManager;
import cx.arcane.managers.priceManager.PriceManager;
import cx.arcane.managers.publicBotManager.PublicBotManager;
import cx.arcane.managers.pvpManager.PVPManager;
import cx.arcane.managers.skinManager.SkinManager;
import cx.arcane.managers.teleportManager.TeleportManager;
import cx.arcane.managers.tpaManager.TPAManager;
import cx.arcane.managers.updateManager.UpdateManager;
import cx.arcane.managers.voteManager.VoteManager;
import cx.arcane.utils.Colors;
import cx.arcane.utils.Log;
import cx.arcane.utils.Text;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import io.github.retrooper.packetevents.util.folia.TaskWrapper;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public final class Arcane extends JavaPlugin {

    private static final AtomicBoolean severeFailure = new AtomicBoolean(false);
    private static final AtomicBoolean managersEnabled = new AtomicBoolean(false);
    private static final AtomicBoolean managersDisabled = new AtomicBoolean(false);
    private static final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private static final AtomicReference<TaskWrapper> autoSaveTask = new AtomicReference<>(null);
    private static final ReentrantLock saveLock = new ReentrantLock();

    public static AtomicBoolean getShuttingDown() { return shuttingDown; }
    public static ReentrantLock getSaveLock() { return saveLock; }

    @Override
    public void onLoad() {
        getConfig().options().copyDefaults(true);
        saveConfig();

        try {
            DependencyManager.onLoad();
            PluginManager.onLoad();
        } catch (Exception e) {
            e.printStackTrace();
            Log.error("Arcane failed to load properly. Shutting down server...");
            severeFailure.set(true);
            getPlugin().getServer().shutdown();
            return;
        }

        Component purpleAscii = Component.text("""
           __ _ _ __ ___ __ _ _ __   ___
          / _` | '__/ __/ _` | '_ \\ / _ \\
         | (_| | | | (_| (_| | | | |  __/
          \\__,_|_|  \\___\\__,_|_| |_|\\___|
        """.stripIndent(), Colors.HOT_PINK);

        getComponentLogger().info(purpleAscii);
        Log.info("Arcane has been loaded!");
    }

    @Override
    public void onEnable() {
        if (severeFailure.get()) return;

        getServer().motd(Component.text("Arcane is booting up...", Colors.GRAY));
        enableManagers();
        if (managersEnabled.get()) {
            startAutoSave();
            Log.info("Arcane has been enabled!");
            Log.info("Running shop prices check...");
            PriceManager.checkShopPrices(Bukkit.getConsoleSender());
            Log.info("Prices check done.");
        } else {
            Log.error("Arcane failed to enable properly. Shutting server down...");

        }
    }

    @Override
    public void onDisable() {
        if (severeFailure.get()) return;

        if (!managersDisabled.get()) {
            Log.error("Managers weren't disabled cleanly. Saving now...");
            disableManagers();
        }
        Log.info("Arcane has been disabled!");
    }

    public static void onServerStop() {
        if (severeFailure.get()) return;

        getPlugin().getServer().motd(Component.text("Arcane is shutting down...", Colors.GRAY));
        Log.info("Arcane is shutting down...");

        stopAutoSave();
        PVPManager.endCombat();

        for (Player p : Bukkit.getOnlinePlayers())
            p.kick(Component.text(Text.toSmallCaps("Arcane is shutting down"), Colors.HOT_PINK));

        FoliaScheduler.getAsyncScheduler().runNow(getPlugin(), t -> {
            Log.info("Waiting for all players to disconnect before shutdown.");

            long deadline = System.currentTimeMillis() + 30_000L;
            while (!Bukkit.getOnlinePlayers().isEmpty()) {
                if (System.currentTimeMillis() >= deadline) {
                    Log.warn("Timed out waiting for players to disconnect. Proceeding anyway.");
                    break;
                }
                Thread.onSpinWait();
            }

            if (Bukkit.getOnlinePlayers().isEmpty())
                Log.info("All players gone. Please wait...");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            disableManagers();
            getPlugin().getServer().shutdown();
        });
    }

    private static void startAutoSave() {
        if (severeFailure.get()) return;

        TaskWrapper task = FoliaScheduler.getAsyncScheduler().runAtFixedRate(
                getPlugin(),
                t -> {
                    if (shuttingDown.get()) return;
                    if (severeFailure.get()) return;

                    if (!saveLock.tryLock()) {
                        Log.info("Auto-save skipped — save already in progress.");
                        return;
                    }
                    try {
                        Log.info("Auto-saving...");
                        saveManagers();
                        Log.info("Auto-save complete.");
                    } finally {
                        saveLock.unlock();
                    }
                },
                30L, 30L, TimeUnit.SECONDS
        );
        autoSaveTask.set(task);
    }

    private static void stopAutoSave() {
        TaskWrapper task = autoSaveTask.getAndSet(null);
        if (task != null) task.cancel();
    }

    public static void enableManagers() {
        PluginManager.onEnable();
        DiscordManager.onEnable();
        DBManager.onEnable();
        MOTDManager.onEnable();
        ItemManager.onEnable();
        PriceManager.onEnable();
        PlayerManager.onEnable();
        GeoManager.onEnable();
        AuthManager.onEnable();
        CommandManager.onEnable();
        PlaceholderManager.onEnable();
        TeleportManager.onEnable();
        TPAManager.onEnable();
        VoteManager.onEnable();
        PermissionManager.onEnable();
        ChatManager.onEnable();
        EcoManager.onEnable();
        SkinManager.onEnable();
        CrateManager.onEnable();
        UpdateManager.onEnable();
        HomeManager.onEnable();
        InteractionManager.onEnable();
        PVPManager.onEnable();
        PublicBotManager.onEnable();
        CoinFlipManager.onEnable();
        LoreManager.onEnable();
        AntiXrayManager.onEnable();
        AuctionManager.onEnable();
        GizmoManager.onEnable();
        ClanManager.onEnable();
        BountyManager.onEnable();
        OrdersManager.onEnable();
        managersEnabled.set(true);
    }

    public static void saveManagers() {
        ClanManager.onSave();
        AuctionManager.onSave();
        CoinFlipManager.onSave();
        HomeManager.onSave();
        CrateManager.onSave();
        SkinManager.onSave();
        EcoManager.onSave();
        VoteManager.onSave();
        PlayerManager.onSave();
        PriceManager.onSave();
        BountyManager.onSave();
        OrdersManager.onSave();
    }

    public static void disableManagers() {
        Log.info("Disabling managers...");

        saveLock.lock();
        try {
            OrdersManager.onDisable();
            BountyManager.onDisable();
            ClanManager.onDisable();
            GizmoManager.onDisable();
            AuctionManager.onDisable();
            AntiXrayManager.onDisable();
            LoreManager.onDisable();
            CoinFlipManager.onDisable();
            PublicBotManager.onDisable();
            PVPManager.onDisable();
            InteractionManager.onDisable();
            HomeManager.onDisable();
            UpdateManager.onDisable();
            CrateManager.onDisable();
            SkinManager.onDisable();
            EcoManager.onDisable();
            ChatManager.onDisable();
            PermissionManager.onDisable();
            VoteManager.onDisable();
            TPAManager.onDisable();
            TeleportManager.onDisable();
            PlaceholderManager.onDisable();
            CommandManager.onDisable();
            AuthManager.onDisable();
            GeoManager.onDisable();
            PlayerManager.onDisable();
            PriceManager.onDisable();
            ItemManager.onDisable();
            MOTDManager.onDisable();
            DBManager.onDisable();
            DiscordManager.onDisable();
            PluginManager.onDisable();
        } finally {
            saveLock.unlock();
        }

        Log.info("Managers disabled.");
        managersDisabled.set(true);
    }

    public static Arcane getPlugin() {
        return JavaPlugin.getPlugin(Arcane.class);
    }
}