package cx.arcane.managers.clanManager;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ClanInvite {
    private UUID clanId;
    private UUID inviterId;
    private UUID targetPlayerId;
    private Instant expiresAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
