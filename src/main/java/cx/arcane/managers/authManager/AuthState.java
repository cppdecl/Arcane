package cx.arcane.managers.authManager;

import java.time.Instant;

public class AuthState {

    private Instant startedAt;
    private boolean isAuthenticated;
    private long invalidPasswordCount;

    public Instant getStartedAt() {
        return this.startedAt;
    }
    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }
    public boolean isAuthenticated() {
        return this.isAuthenticated;
    }
    public void setAuthenticated(boolean isAuthenticated) {
        this.isAuthenticated = isAuthenticated;
    }
    public long getInvalidPasswordCount() {
        return this.invalidPasswordCount;
    }
    public void setInvalidPasswordCount(long invalidPasswordCount) {
        this.invalidPasswordCount = invalidPasswordCount;
    }
    public void incrementInvalidPasswordCount() {
        this.invalidPasswordCount++;
    }
}

