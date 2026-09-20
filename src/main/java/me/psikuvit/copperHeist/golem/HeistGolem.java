package me.psikuvit.copperHeist.golem;

import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.loot.LootItem;
import org.bukkit.Location;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.CopperGolem.Oxidizing;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Wraps a spawned copper golem entity with the match state the plugin needs: its team, stun and oxidation timing, and what it
 * is holding. The golem's actual behaviour (finding copper chests, carrying items to chests, wandering) is vanilla; the plugin
 * only watches it (see {@link GolemManager}). What a golem "carries" is simply the item in its hand.
 */
public class HeistGolem {

    private final CopperGolem entity;
    private final Team team;
    private final Location home;
    private final List<Location> waypoints;

    private TextDisplay label;
    private int number;

    private long stunUntilMillis = 0;
    private long stunImmuneUntilMillis = 0;

    private long stageChangedAtMillis = System.currentTimeMillis();
    private long stageDurationMillis;
    private long waxedUntilMillis = 0;

    // what the last watch saw - used to report changes and to time how long the golem has held its item
    private CopperGolem.State lastState = CopperGolem.State.IDLE;
    private String holdingId;
    private long holdingSinceMillis;
    private long lastGuardNoticeMillis;

    public HeistGolem(CopperGolem entity, Team team, Location home, List<Location> waypoints) {
        this.entity = entity;
        this.team = team;
        this.home = home;
        this.waypoints = waypoints;
    }

    public CopperGolem getEntity() {
        return entity;
    }

    public Team getTeam() {
        return team;
    }

    /** The team's golem idle point - where the golem spawns and where guards send it back to. */
    public Location getHome() {
        return home;
    }

    public List<Location> getWaypoints() {
        return waypoints;
    }

    /** 1, 2, 3... within the team - only used to tell golems apart in debug output. */
    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String debugName() {
        return team.name().toLowerCase() + "#" + number;
    }

    public TextDisplay getLabel() {
        return label;
    }

    public void setLabel(TextDisplay label) {
        this.label = label;
    }

    // ---- what it holds ----

    /** A copy of the loot stack in the golem's hand, or null if it holds nothing (or something that isn't loot). */
    public ItemStack getCarried() {
        ItemStack hand = entity.getEquipment().getItemInMainHand();
        return isLoot(hand) ? hand.clone() : null;
    }

    /** Puts a stack in the golem's hand, or empties the hand for null. */
    public void setCarried(ItemStack carried) {
        EntityEquipment equipment = entity.getEquipment();
        equipment.setItemInMainHand(carried == null ? ItemStack.empty() : carried);
        equipment.setItemInMainHandDropChance(0f);
    }

    public boolean isCarrying() {
        return isLoot(entity.getEquipment().getItemInMainHand());
    }

    private static boolean isLoot(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0 && LootItem.isLoot(item);
    }

    // ---- stun ----

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

    // ---- oxidation ----

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

    // ---- watch bookkeeping (GolemManager only) ----

    CopperGolem.State lastState() {
        return lastState;
    }

    void lastState(CopperGolem.State state) {
        this.lastState = state;
    }

    String holdingId() {
        return holdingId;
    }

    void holding(String lootId, long now) {
        this.holdingId = lootId;
        this.holdingSinceMillis = now;
    }

    long holdingSinceMillis() {
        return holdingSinceMillis;
    }

    long lastGuardNoticeMillis() {
        return lastGuardNoticeMillis;
    }

    void lastGuardNoticeMillis(long now) {
        this.lastGuardNoticeMillis = now;
    }
}
