package cx.arcane.managers.playerManager;

import lombok.Data;

@Data
public class PlayerSettings {

    private boolean showPublicChats = true;
    private boolean showPrivateMessages = true;
    private boolean showDeathMessages = true;
    private boolean showSystemMessages = true;
    private boolean showPunishmentMessages = true;
    private boolean useProfanityFilter = false;
    private boolean useSpamFilter = true;

    private boolean allowTpaRequests = true;
    private boolean autoAcceptTpaRequests = false;
    private boolean autoAcceptTpaHereRequests = false;
    private boolean allowPayments = true;
    private boolean nightVision = false;
    private boolean anonymous = false;
    private boolean allowMobSpawning = true;
}