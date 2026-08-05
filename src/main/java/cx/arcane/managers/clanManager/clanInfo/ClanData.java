package cx.arcane.managers.clanManager.clanInfo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cx.arcane.managers.playerManager.PlayerData;
import cx.arcane.managers.playerManager.PlayerManager;
import cx.arcane.utils.LocationUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClanData {
    private UUID uniqueId;
    private String tag;

    private Instant createdAt;
    private UUID createdById;

    private boolean friendlyFireEnabled = false;

    private long kills = 0;
    private long deaths = 0;

    @JsonIgnore
    private Location home;
    private String homeData;

    private ConcurrentHashMap<UUID, ClanMember> members = new ConcurrentHashMap<>();

    @JsonIgnore
    public void setHome(Location home) {
        this.home = home;
        this.homeData = LocationUtils.serializePlain(home); // null-safe
    }

    @JsonIgnore
    public Location getHome() {
        if (home != null) return home;
        if (homeData == null) return null;
        home = LocationUtils.deserializePlain(homeData);
        return home;
    }

    @JsonIgnore
    public List<ClanMember> getMemberList() {
        return new ArrayList<>(members.values());
    }

    @JsonIgnore
    public int getMemberCount() {
        return members.size();
    }

    @JsonIgnore
    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    @JsonIgnore
    public ClanMember getMember(UUID playerId) {
        return members.get(playerId);
    }

    @JsonIgnore
    public void addMember(ClanMember member) {
        members.put(member.getUniqueId(), member);
    }

    @JsonIgnore
    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    @JsonIgnore
    public ClanMember getLeader() {
        for (ClanMember member : members.values()) {
            if (Objects.equals(member.getRank(), "Leader")) {
                return member;
            }
        }
        return null;
    }

    @JsonIgnore
    public int getMaxMembers() {
        return 12;
    }

    @JsonIgnore
    public void broadcast(Component message) {
        for (ClanMember member : members.values()) {
            PlayerData pData = PlayerManager.getByUniqueId(member.getUniqueId());
            if (pData == null || !pData.isOnline()) continue;
            Player p = pData.getPlayer();
            p.sendMessage(message);
        }
    }

    @JsonIgnore
    public void broadcast(Component message, Sound sfx, float volume, float pitch) {
        for (ClanMember member : members.values()) {
            PlayerData pData = PlayerManager.getByUniqueId(member.getUniqueId());
            if (pData == null || !pData.isOnline()) continue;
            Player p = pData.getPlayer();
            p.sendMessage(message);
            p.playSound(p.getLocation(), sfx, volume, pitch);
        }
    }

    @JsonIgnore
    public void broadcast(Component message, Sound sfx) {
        broadcast(message, sfx, 1.0f, 1.0f);
    }
}

