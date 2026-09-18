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

    public SavedState getSavedState() {
        return savedState;
    }

    public void setSavedState(SavedState savedState) {
        this.savedState = savedState;
    }
}
