package cx.arcane.managers.playerManager;

import lombok.Data;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;

@Data
public class PlayerSession {
    private InetSocketAddress address;
    private String username;
    private UUID uniqueId;
    private AccountType accountType;
    private Instant startedAt;
}
