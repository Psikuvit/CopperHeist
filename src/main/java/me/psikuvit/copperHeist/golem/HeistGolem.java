package me.psikuvit.copperHeist.golem;

import me.psikuvit.copperHeist.game.Team;
import org.bukkit.Location;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps a spawned copper golem entity with the match state DeliveryGoal needs:
 * which leg of the dock<->vault trip it's on, what it's carrying, and stun state.
 */
public class HeistGolem {

    public enum Phase {
        AT_DOCK,
        TO_VAULT,
        TO_DOCK
    }

    private final CopperGolem entity;
    private final Team team;
    private final Location dockIdle;
    private final List<Location> waypointsToVault;
    private final List<Location> waypointsToDock;

    private TextDisplay label;
    private Phase phase = Phase.AT_DOCK;
    private int waypointIndex = 0;
    private ItemStack carried;
    private Location currentTarget;

    private long stunUntilMillis = 0;
    private long stunImmuneUntilMillis = 0;

    private Location lastPosition;
    private long lastMovedMillis = System.currentTimeMillis();

    private long stageChangedAtMillis = System.currentTimeMillis();
    private long stageDurationMillis;

    public HeistGolem(CopperGolem entity, Team team, Location dockIdle, List<Location> waypointsToVault) {
        this.entity = entity;
        this.team = team;
        this.dockIdle = dockIdle;
        this.waypointsToVault = waypointsToVault;
        List<Location> reversed = new ArrayList<>(waypointsToVault);
        Collections.reverse(reversed);
        reversed.add(dockIdle);
        this.waypointsToDock = reversed;
    }

    public CopperGolem getEntity() {
        return entity;
    }

    public Team getTeam() {
        return team;
    }

    public Location getDockIdle() {
        return dockIdle;
    }

    public List<Location> getWaypointsToVault() {
        return waypointsToVault;
    }

    public List<Location> getWaypointsToDock() {
        return waypointsToDock;
    }

    public Location getCurrentTarget() {
        return currentTarget;
    }

    public void setCurrentTarget(Location currentTarget) {
        this.currentTarget = currentTarget;
    }

    public TextDisplay getLabel() {
        return label;
    }

    public void setLabel(TextDisplay label) {
        this.label = label;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public int getWaypointIndex() {
        return waypointIndex;
    }

    public void setWaypointIndex(int waypointIndex) {
        this.waypointIndex = waypointIndex;
    }

    public ItemStack getCarried() {
        return carried;
    }

    public void setCarried(ItemStack carried) {
        this.carried = carried;
    }

    public boolean isCarrying() {
        return carried != null && carried.getAmount() > 0;
    }

    public boolean isStunned() {
        return System.currentTimeMillis() < stunUntilMillis;
    }

    public boolean isStunImmune() {
        return System.currentTimeMillis() < stunImmuneUntilMillis;
    }

    public void stun(long seconds, long immunitySeconds) {
        long now = System.currentTimeMillis();
        stunUntilMillis = now + seconds * 1000;
        stunImmuneUntilMillis = stunUntilMillis + immunitySeconds * 1000;
    }

    public Location getLastPosition() {
        return lastPosition;
    }

    public void setLastPosition(Location lastPosition) {
        this.lastPosition = lastPosition;
    }

    public long getLastMovedMillis() {
        return lastMovedMillis;
    }

    public void setLastMovedMillis(long lastMovedMillis) {
        this.lastMovedMillis = lastMovedMillis;
    }

    public long getStageChangedAtMillis() {
        return stageChangedAtMillis;
    }

    public void setStageChangedAtMillis(long stageChangedAtMillis) {
        this.stageChangedAtMillis = stageChangedAtMillis;
    }

    public long getStageDurationMillis() {
        return stageDurationMillis;
    }

    public void setStageDurationMillis(long stageDurationMillis) {
        this.stageDurationMillis = stageDurationMillis;
    }
}
