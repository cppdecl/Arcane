package cx.arcane.managers.pvpManager;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PVPSession {

    private boolean isFirstHit = true;

    private final UUID playerId;
    private UUID lastAttackerId;
    private UUID firstAttackerId;
    private UUID highestDmgAttackerId;

    private double highestDmgReceived = 0;

    private PVPManager.ExplosiveType lastExplosiveType;

    /**
     * The weapon ItemStack from the last registered hit that updated this session.
     * Stored as a pre-cloned snapshot so inventory changes after the hit don't mutate it.
     * Null if the source weapon could not be resolved (bare hand, unknown projectile, etc.).
     * Used by the death message system to build a hoverable weapon component.
     */
    private ItemStack attackerWeapon;

    private final List<UUID> attackers = new ArrayList<>();

    private long lastDamageTime = 0;
    private long cooldownMs;

    /**
     * @param playerId   UUID of the player this session belongs to.
     * @param cooldownMs Milliseconds of inactivity before combat expires.
     *                   A 10 ms internal buffer is added to prevent edge-case premature expiry.
     */
    public PVPSession(UUID playerId, long cooldownMs) {
        this.playerId   = playerId;
        this.cooldownMs = cooldownMs + 10;
    }

    /** @return True only on the very first hit of this session; used to gate the combat-entry notification. */
    public boolean isFirstHit()                  { return isFirstHit; }
    public void setFirstHit(boolean v)           { isFirstHit = v; }

    /** @return UUID of the player this session belongs to. */
    public UUID getPlayerId()                    { return playerId; }

    /** @return UUID of the most recent attacker, updated on every {@code addAttacker} call. */
    public UUID getLastAttackerId()              { return lastAttackerId; }
    public void setLastAttackerId(UUID v)        { lastAttackerId = v; }

    /**
     * @return A pre-cloned ItemStack representing the weapon of the last recorded hit.
     *         Null when the weapon could not be resolved (bare hand or untracked source).
     *         Used directly in death message construction — do not mutate the returned reference.
     */
    public ItemStack getAttackerWeapon()         { return attackerWeapon; }

    /**
     * @param v A pre-cloned ItemStack to store. The caller must ensure this is already
     *          cloned before passing — this setter does not clone defensively.
     */
    public void setAttackerWeapon(ItemStack v)   { attackerWeapon = v; }

    /** @return Epoch-millisecond timestamp of the most recent damage event in this session. */
    public long getLastDamageTime()              { return lastDamageTime; }
    public void setLastDamageTime(long v)        { lastDamageTime = v; }

    /** @return Combat cooldown in milliseconds (includes the internal 10 ms buffer). */
    public long getCooldownMs()                  { return cooldownMs; }
    public void setCooldownMs(long v)            { cooldownMs = v; }

    /**
     * @return True if the elapsed time since the last damage event is still within the cooldown window.
     *         Thread-safe — reads a volatile-equivalent long from {@link System#currentTimeMillis()}.
     */
    public boolean isInCombat() {
        return System.currentTimeMillis() - lastDamageTime < cooldownMs;
    }

    /** Stamps {@code lastDamageTime} to the current wall-clock time, restarting the combat cooldown. */
    public void resetCooldown() {
        lastDamageTime = System.currentTimeMillis();
    }

    /**
     * Registers an attacker UUID for this session. Duplicate entries are suppressed.
     * Also maintains {@code firstAttackerId} (immutable after first set) and
     * {@code lastAttackerId} (always updated to the most recent entry).
     *
     * @param attackerId UUID of the player to register as an attacker.
     */
    public void addAttacker(UUID attackerId) {
        if (!attackers.contains(attackerId)) attackers.add(attackerId);
        if (firstAttackerId == null) firstAttackerId = attackerId;
        lastAttackerId = attackerId;
    }
}