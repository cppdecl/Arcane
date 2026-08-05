package cx.arcane.managers.crateManager;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class CrateData {

    @JsonProperty("id")
    private String id;

    @JsonProperty("colorValue")
    private int colorValue;

    @JsonProperty("descriptionJson")
    private String descriptionJson;

    @JsonProperty("world")
    private String world;

    @JsonProperty("x")
    private double x;

    @JsonProperty("y")
    private double y;

    @JsonProperty("z")
    private double z;

    @JsonProperty("yaw")
    private float yaw;

    @JsonProperty("pitch")
    private float pitch;

    public CrateData() {}

    public CrateData(String id, Location location, TextColor color, Component description) {
        this.id = id;
        this.colorValue = color.value();
        this.descriptionJson = GsonComponentSerializer.gson().serialize(description);
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getColorValue() { return colorValue; }
    public void setColorValue(int colorValue) { this.colorValue = colorValue; }

    public String getDescriptionJson() { return descriptionJson; }
    public void setDescriptionJson(String descriptionJson) { this.descriptionJson = descriptionJson; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }

    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }

    @JsonIgnore
    public TextColor getColor() { return TextColor.color(colorValue); }

    @JsonIgnore
    public void setColor(TextColor color) {
        this.colorValue = color.value();
        CrateHologramManager.createHolograms(this);
    }

    @JsonIgnore
    public Component getDescription() {
        return descriptionJson == null ? Component.empty() : GsonComponentSerializer.gson().deserialize(descriptionJson);
    }

    @JsonIgnore
    public void setDescription(Component description) {
        this.descriptionJson = GsonComponentSerializer.gson().serialize(description);
        CrateHologramManager.createHolograms(this);
    }

    @JsonIgnore
    public Location getLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    @JsonIgnore
    public void setLocation(Location location) {
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
        CrateHologramManager.createHolograms(this);
    }

    @JsonIgnore
    public void destroy() {
        CrateHologramManager.removeHolograms(this);
        CrateManager.getKeyData().values().forEach(kd -> kd.removeKey(id));
        CrateManager.removeCrate(id);
    }
}