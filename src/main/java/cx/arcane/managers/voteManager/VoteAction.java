package cx.arcane.managers.voteManager;

import lombok.Data;

import java.util.UUID;

@Data
public class VoteAction {
    private UUID uniqueId;
    private String username;

    private String serviceName;
    private String address;
    private long voteTimestamp;
    private long receivedTimestamp;
    private String rawPayload;
}