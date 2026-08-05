package cx.arcane.managers.playerManager;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import cx.arcane.managers.crateManager.CrateHologramManager;
import lombok.Data;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@Data
public class PlayerMeta {
    private boolean zeus = false;
    private boolean flight = false;
    private boolean antiXrayDebug = false;
    private boolean mobSpawningDebug = false;

    // statistics
    private long kills = 0;
    private long deaths = 0;
    private boolean killstreakActive = false;
    private long killstreak = 0;
    private long playtimeSeconds = 0;
    private long anchorsExploded = 0;
    private long crystalsExploded = 0;
    private long totalBought = 0;
    private long totalSold = 0;
    private long totalVotes = 0;
    private long totalAuctionEarned = 0;
    private long totalAuctionSpent = 0;

    private long elo = 0;

    private boolean vanish = false;

    private boolean stupid = false;
}
