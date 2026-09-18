package me.psikuvit.copperHeist.golem;

import me.psikuvit.copperHeist.game.Team;
import org.bukkit.Location;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.CopperGolem.Oxidizing;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps a spawned copper golem entity with the match state it needs: its
 * route, what it's carrying, stun state, and oxidation timing. Movement and
 * AI decisions are delegated entirely to its {@link GolemBrain}.
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
    private final GolemBrain brain;

    private TextDisplay label;
    private ItemStack carried;

    private long stunUntilMillis = 0;
    private long stunImmuneUntilMillis = 0;

    private long stageChangedAtMillis = System.currentTimeMillis();
    private long stageDurationMillis;
    private long waxedUntilMillis = 0;

    public HeistGolem(CopperGolem entity, Team team, Location dockIdle, List<Location> waypointsToVault, GolemManager manager) {
        this.entity = entity;
        this.team = team;
        this.dockIdle = dockIdle;
        this.waypointsToVault = waypointsToVault;
        List<Location> reversed = new ArrayList<>(waypointsToVault);
        Collections.reverse(reversed);
        reversed.add(dockIdle);
        this.waypointsToDock = reversed;
        this.brain = new GolemBrain(this, manager);
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

    public GolemBrain getBrain() {
        return brain;
    }

    public TextDisplay getLabel() {
        return label;
    }

    public void setLabel(TextDisplay label) {
        this.label = label;
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

    /** Mechanic's instant recover - clears the stun without granting the usual post-stun immunity window. */
    public void clearStun() {
        stunUntilMillis = 0;
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

    public boolean isWaxed() {
        return entity.getOxidizing() instanceof Oxidizing.Waxed;
    }

    public long getWaxedUntilMillis() {
        return waxedUntilMillis;
    }

    public void setWaxedUntilMillis(long waxedUntilMillis) {
        this.waxedUntilMillis = waxedUntilMillis;
    }
}
