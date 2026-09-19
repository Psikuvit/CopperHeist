package me.psikuvit.copperHeist.golem;

import com.destroystokyo.paper.entity.Pathfinder;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Owns one golem's actual AI: which leg of the dock<->vault trip it's on, path movement via
 * Pathfinder#moveTo waypoint by waypoint, stuck recovery and the pick-up / drop-off animations.
 * Every HeistGolem has exactly one of these. DeliveryGoal is just the thin Paper Mob Goal adapter
 * that calls into it each tick - all the behavior itself lives here so it isn't tied to the Goal API's shape.
 *
 * Movement details worth knowing:
 * - Intermediate waypoints count as reached a little early (golems.ai.corner-cut-distance) so the golem
 *   flows through corners instead of stopping at every point.
 * - A path is re-issued whenever it ends early, the golem's speed changes (oxidation), or it resumes after a
 *   stun; a waypoint that keeps failing to path is skipped (or, if it is the last one, jumped to).
 * - Being stuck first triggers a fresh path, and only later a teleport to the current waypoint.
 */
public class GolemBrain {

    private final HeistGolem golem;
    private final GolemManager manager;

    private HeistGolem.Phase phase = HeistGolem.Phase.AT_DOCK;
    private int waypointIndex = 0;
    private Location currentTarget;
    private double lastSpeed;
    private int pathFailures;
    private long lastRepathMillis;
    private Location lastPosition;
    private long lastMovedMillis = System.currentTimeMillis();
    private boolean repathedWhileStuck;
    private long offPathSinceMillis = 0;

    private long pausedUntilMillis = 0;
    private boolean handLocked = false;
    private long lastDizzyMillis = 0;

    public GolemBrain(HeistGolem golem, GolemManager manager) {
        this.golem = golem;
        this.manager = manager;
    }

    private double arriveDistance() {
        return manager.settings().getDouble("golems.ai.arrive-distance", 1.5);
    }

    private double cornerCutDistance() {
        return manager.settings().getDouble("golems.ai.corner-cut-distance", 1.5);
    }

    private long stuckMillis() {
        return (long) (manager.settings().getDouble("golems.ai.stuck-seconds", 8.0) * 1000);
    }

    private long repathMillis() {
        return (long) (manager.settings().getDouble("golems.ai.repath-seconds", 0.5) * 1000);
    }

    private double offPathDistanceSquared() {
        double d = manager.settings().getDouble("golems.ai.off-path-distance", 4.0);
        return d * d;
    }

    private long offPathMillis() {
        return (long) (manager.settings().getDouble("golems.ai.off-path-seconds", 2.0) * 1000);
    }

    public boolean shouldActivate() {
        return !golem.getEntity().isDead();
    }

    /** True while a drop-off animation is playing, so the held item stays visible until it finishes. */
    public boolean holdsHandVisual() {
        return handLocked;
    }

    public void start() {
        Pathfinder pathfinder = golem.getEntity().getPathfinder();
        pathfinder.setCanOpenDoors(true);
        pathfinder.setCanPassDoors(true);
        pathfinder.setCanFloat(true);
        lastPosition = golem.getEntity().getLocation();
        lastMovedMillis = System.currentTimeMillis();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (!manager.isInBounds(golem.getEntity().getLocation())) {
            returnToDock();
            return;
        }
        if (handLocked && now >= pausedUntilMillis) endAnimation();

        if (golem.isStunned()) {
            halt(now);
            if (manager.effects().enabled() && now - lastDizzyMillis >= 500) {
                lastDizzyMillis = now;
                manager.effects().dizzy(golem.getEntity().getLocation());
            }
            return;
        }

        double speed = manager.currentSpeed(golem);
        if (speed <= 0.0) {
            // Fully oxidized: frozen statue until scraped.
            halt(now);
            return;
        }
        if (now < pausedUntilMillis) {
            halt(now);
            return;
        }

        switch (phase) {
            case AT_DOCK -> tickAtDock();
            case TO_VAULT -> tickTravelling(golem.getWaypointsToVault(), true, speed, now);
            case TO_DOCK -> tickTravelling(golem.getWaypointsToDock(), false, speed, now);
        }

        checkStuck(now);
        checkOffPath();
    }

    /** Stand still and forget the current path, so movement is re-issued cleanly once the golem may move again. */
    private void halt(long now) {
        golem.getEntity().getPathfinder().stopPathfinding();
        currentTarget = null;
        lastMovedMillis = now;
        lastPosition = golem.getEntity().getLocation();
    }

    /** Fell out of the arena (or got launched out of it) - back to the idle point, carrying whatever it had. */
    private void returnToDock() {
        golem.getEntity().teleport(golem.getDockIdle(), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        phase = HeistGolem.Phase.AT_DOCK;
        waypointIndex = 0;
        currentTarget = null;
        lastPosition = golem.getEntity().getLocation();
        lastMovedMillis = System.currentTimeMillis();
    }

    /** Knocked well away from its route (wind charge, explosion): after a couple of seconds, re-path from the nearest waypoint. */
    private void checkOffPath() {
        if (phase == HeistGolem.Phase.AT_DOCK) {
            offPathSinceMillis = 0;
            return;
        }
        List<Location> route = phase == HeistGolem.Phase.TO_VAULT ? golem.getWaypointsToVault() : golem.getWaypointsToDock();
        if (route.isEmpty()) return;

        Location here = golem.getEntity().getLocation();
        int nearest = 0;
        double nearestSquared = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            Location point = route.get(i);
            if (point.getWorld() != here.getWorld()) continue;
            double d = here.distanceSquared(point);
            if (d < nearestSquared) {
                nearestSquared = d;
                nearest = i;
            }
        }
        if (nearestSquared <= offPathDistanceSquared()) {
            offPathSinceMillis = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (offPathSinceMillis == 0) {
            offPathSinceMillis = now;
        } else if (now - offPathSinceMillis >= offPathMillis()) {
            waypointIndex = nearest;
            currentTarget = null;
            offPathSinceMillis = 0;
        }
    }

    private void tickAtDock() {
        if (golem.isCarrying()) {
            beginTrip();
            return;
        }
        ItemStack taken = manager.tryTakeFromDock(golem.getTeam());
        if (taken != null) {
            golem.setCarried(taken);
            manager.updateLabel(golem);
            manager.effects().pickedUp(golem.getEntity().getLocation());
            animate(CopperGolem.State.GETTING_ITEM, false);
            beginTrip();
        }
    }

    private void beginTrip() {
        phase = HeistGolem.Phase.TO_VAULT;
        waypointIndex = 0;
        currentTarget = null;
    }

    private void tickTravelling(List<Location> route, boolean toVault, double speed, long now) {
        if (waypointIndex >= route.size()) {
            arrive(toVault);
            return;
        }

        Location target = route.get(waypointIndex);
        boolean last = waypointIndex == route.size() - 1;
        Location here = golem.getEntity().getLocation();
        if (reached(here, target, arriveDistance() + (last ? 0 : cornerCutDistance()))) {
            waypointIndex++;
            currentTarget = null;
            pathFailures = 0;
            return;
        }

        Pathfinder pathfinder = golem.getEntity().getPathfinder();
        boolean pathEnded = !pathfinder.hasPath() && now - lastRepathMillis >= repathMillis();
        if (currentTarget == null || Math.abs(speed - lastSpeed) > 1.0E-6 || pathEnded) {
            boolean found = pathfinder.moveTo(target, speed);
            currentTarget = target;
            lastSpeed = speed;
            lastRepathMillis = now;
            if (found) {
                pathFailures = 0;
            } else if (++pathFailures >= Math.max(1, manager.settings().getInt("golems.ai.max-path-failures", 3))) {
                giveUpOn(target, last);
            }
        }
    }

    /** A waypoint that can't be pathed to: skip it, or if it's the final one, jump straight onto it. */
    private void giveUpOn(Location target, boolean last) {
        if (last) golem.getEntity().teleport(target, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        waypointIndex++;
        currentTarget = null;
        pathFailures = 0;
    }

    private static boolean reached(Location here, Location target, double distance) {
        if (here.getWorld() != target.getWorld()) return false;
        double dx = here.getX() - target.getX();
        double dz = here.getZ() - target.getZ();
        return dx * dx + dz * dz <= distance * distance && Math.abs(here.getY() - target.getY()) <= 2.5;
    }

    private void arrive(boolean toVault) {
        if (toVault) {
            ItemStack shown = golem.getCarried();
            manager.deposit(golem);
            if (shown != null) {
                golem.showInHand(shown);
                manager.effects().deposited(golem.getEntity().getLocation());
                animate(CopperGolem.State.DROPPING_ITEM, true);
            }
            phase = HeistGolem.Phase.TO_DOCK;
        } else {
            phase = HeistGolem.Phase.AT_DOCK;
        }
        waypointIndex = 0;
        currentTarget = null;
    }

    /** Plays one of the golem's own arm animations and holds it in place for golems.animation.pause-seconds. */
    private void animate(CopperGolem.State state, boolean keepHandItem) {
        if (!manager.effects().enabled()) return;
        golem.getEntity().setGolemState(state);
        pausedUntilMillis = System.currentTimeMillis()
                + (long) (manager.settings().getDouble("golems.animation.pause-seconds", 1.0) * 1000);
        handLocked = keepHandItem;
        golem.getEntity().getPathfinder().stopPathfinding();
    }

    private void endAnimation() {
        handLocked = false;
        golem.getEntity().setGolemState(CopperGolem.State.IDLE);
        if (!golem.isCarrying()) golem.showInHand(null);
    }

    private void checkStuck(long now) {
        if (phase == HeistGolem.Phase.AT_DOCK) {
            // Waiting for loot at the dock isn't being stuck.
            lastMovedMillis = now;
            lastPosition = golem.getEntity().getLocation();
            return;
        }
        Location here = golem.getEntity().getLocation();
        if (lastPosition == null || here.distanceSquared(lastPosition) > 1.0) {
            lastPosition = here;
            lastMovedMillis = now;
            repathedWhileStuck = false;
            return;
        }
        long idle = now - lastMovedMillis;
        if (idle > stuckMillis()) {
            List<Location> route = phase == HeistGolem.Phase.TO_DOCK
                    ? golem.getWaypointsToDock()
                    : golem.getWaypointsToVault();
            int index = Math.min(waypointIndex, route.size() - 1);
            if (index >= 0) golem.getEntity().teleport(route.get(index), TeleportFlag.EntityState.RETAIN_PASSENGERS);
            currentTarget = null;
            lastPosition = golem.getEntity().getLocation();
            lastMovedMillis = now;
            repathedWhileStuck = false;
        } else if (idle > stuckMillis() / 2 && !repathedWhileStuck) {
            currentTarget = null;
            repathedWhileStuck = true;
        }
    }
}
