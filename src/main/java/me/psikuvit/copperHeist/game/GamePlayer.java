package me.psikuvit.copperHeist.game;

import me.psikuvit.copperHeist.role.Role;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class GamePlayer {

    public record SavedState(ItemStack[] contents, ItemStack[] armor, Location location,
                              GameMode gameMode, double health, int foodLevel) {
    }

    private final UUID uuid;
    private Team team;
    private Role role = Role.RUNNER;
    private SavedState savedState;
    private int steals;
    private int scrapes;
    private int kills;
    private int delivered;
    private int stolenValue;
    private int relicsDelivered;
    private long protectedUntilMillis;
    private UUID lastAttacker;
    private long lastAttackMillis;

    public GamePlayer(UUID uuid, Team team) {
        this.uuid = uuid;
        this.team = team;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public int getSteals() {
        return steals;
    }

    public void addSteal() {
        steals++;
    }

    public int getScrapes() {
        return scrapes;
    }

    public void addScrape() {
        scrapes++;
    }

    public int getKills() {
        return kills;
    }

    public void addKill() {
        kills++;
    }

    public int getDelivered() {
        return delivered;
    }

    public void addDelivered(int value) {
        delivered += value;
    }

    public int getStolenValue() {
        return stolenValue;
    }

    public void addStolenValue(int value) {
        stolenValue += value;
    }

    public void addRelicDelivered() {
        relicsDelivered++;
    }

    public double mvpScore() {
        return delivered + stolenValue * 1.5 + relicsDelivered * 20 + kills * 3 + scrapes * 2;
    }

    public void recordAttacker(UUID attacker) {
        lastAttacker = attacker;
        lastAttackMillis = System.currentTimeMillis();
    }

    /** Whoever last hit this player within the window, or null - used to credit void/environment deaths. */
    public UUID recentAttacker(int seconds) {
        return System.currentTimeMillis() - lastAttackMillis <= seconds * 1000L ? lastAttacker : null;
    }

    public boolean isProtected() {
        return System.currentTimeMillis() < protectedUntilMillis;
    }

    public void protectFor(int seconds) {
        protectedUntilMillis = System.currentTimeMillis() + seconds * 1000L;
    }

    public SavedState getSavedState() {
        return savedState;
    }

    public void setSavedState(SavedState savedState) {
        this.savedState = savedState;
    }
}
