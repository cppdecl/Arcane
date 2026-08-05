package cx.arcane.managers.clanManager.clanInfo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClanMember {
    private UUID uniqueId;
    private String rank;
    private Instant joinedAt;

    private long kills = 0;
    private long deaths = 0;
}
