package cx.arcane.managers.homeManager;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.bukkit.Location;

import java.util.UUID;

@Data
public class HomeData {
    private String id;
    private UUID ownerId;
    private double x;
    private double y;
    private double z;
    private double pitch;
    private double yaw;
    private String worldName;
    private long createdAt;

    @JsonIgnore
    public Location getLocation() {
        return new Location(
                org.bukkit.Bukkit.getWorld(worldName),
                x,
                y,
                z,
                (float) yaw,
                (float) pitch
        );
    }

    @JsonIgnore
    public void setLocation(Location location) {
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.pitch = location.getPitch();
        this.yaw = location.getYaw();
        this.worldName = location.getWorld().getName();
    }
}
