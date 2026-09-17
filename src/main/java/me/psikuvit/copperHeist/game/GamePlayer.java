package me.psikuvit.copperHeist.game;

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
    private SavedState savedState;

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

    public SavedState getSavedState() {
        return savedState;
    }

    public void setSavedState(SavedState savedState) {
        this.savedState = savedState;
    }
}
