package me.psikuvit.copperHeist.heist;

import me.psikuvit.copperHeist.game.Team;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;

import java.util.UUID;

/**
 * One active vault breach attempt: {@code attacker} placed it on
 * {@code defender}'s vault door. Progress only advances while an attacker is
 * within range (tracked by {@link VaultDrillManager}); health only drops
 * when a defender lands a hit.
 */
public class VaultDrill {

    private final Team attacker;
    private final Team defender;
    private final BlockDisplay display;
    private final TextDisplay progressLabel;
    private final Interaction hitbox;
    private final double maxHealth;
    private final UUID placer;

    private double health;
    private double progressSeconds;

    public VaultDrill(Team attacker, Team defender, BlockDisplay display, TextDisplay progressLabel,
                       Interaction hitbox, double maxHealth, UUID placer) {
        this.placer = placer;
        this.attacker = attacker;
        this.defender = defender;
        this.display = display;
        this.progressLabel = progressLabel;
        this.hitbox = hitbox;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    /** The player who set the drill up - credited when it finishes. */
    public UUID getPlacer() {
        return placer;
    }

    public Team getAttacker() {
        return attacker;
    }

    public Team getDefender() {
        return defender;
    }

    public BlockDisplay getDisplay() {
        return display;
    }

    public TextDisplay getProgressLabel() {
        return progressLabel;
    }

    public Interaction getHitbox() {
        return hitbox;
    }

    public double getHealth() {
        return health;
    }

    public void damage(double amount) {
        health = Math.max(0, health - amount);
    }

    public boolean isDestroyed() {
        return health <= 0;
    }

    public double getProgressSeconds() {
        return progressSeconds;
    }

    public void addProgress(double seconds) {
        progressSeconds += seconds;
    }

    public double progressFraction(double durationSeconds) {
        return Math.min(1.0, progressSeconds / durationSeconds);
    }

    public double healthFraction() {
        return health / maxHealth;
    }
}
