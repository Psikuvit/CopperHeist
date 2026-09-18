package me.psikuvit.copperHeist.golem;

import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Owns one golem's actual AI: which leg of the dock<->vault trip it's on,
 * path movement via Pathfinder#moveTo waypoint by waypoint, and stuck
 * recovery. Every HeistGolem has exactly one of these. DeliveryGoal is just
 * the thin Paper Mob Goal adapter that calls into it each tick - all the
 * behavior itself lives here so it isn't tied to the Goal API's shape.
 */
public class GolemBrain {


    private final HeistGolem golem;
    private final GolemManager manager;

    private HeistGolem.Phase phase = HeistGolem.Phase.AT_DOCK;
    private int waypointIndex = 0;
    private Location currentTarget;
    private Location lastPosition;
    private long lastMovedMillis = System.currentTimeMillis();
    private long offPathSinceMillis = 0;

    public GolemBrain(HeistGolem golem, GolemManager manager) {
        this.golem = golem;
        this.manager = manager;
    }

    private double arriveDistanceSquared() {
        double d = manager.settings().getDouble("golems.ai.arrive-distance", 1.5);
        return d * d;
    }

    private long stuckMillis() {
        return (long) (manager.settings().getDouble("golems.ai.stuck-seconds", 8.0) * 1000);
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

    public void start() {
        lastPosition = golem.getEntity().getLocation();
        lastMovedMillis = System.currentTimeMillis();
    }

    public void tick() {
        if (!manager.isInBounds(golem.getEntity().getLocation())) {
            returnToDock();
            return;
        }
        if (golem.isStunned()) {
            golem.getEntity().getPathfinder().stopPathfinding();
            return;
        }

        double speed = manager.currentSpeed(golem);
        if (speed <= 0.0) {
            // Fully oxidized: frozen statue until scraped.
            golem.getEntity().getPathfinder().stopPathfinding();
            return;
        }

        switch (phase) {
            case AT_DOCK -> tickAtDock();
            case TO_VAULT -> tickTravelling(golem.getWaypointsToVault(), true, speed);
            case TO_DOCK -> tickTravelling(golem.getWaypointsToDock(), false, speed);
        }

        checkStuck();
        checkOffPath();
    }

    /** Fell out of the arena (or got launched out of it) - back to the idle point, carrying whatever it had. */
    private void returnToDock() {
        golem.getEntity().teleport(golem.getDockIdle());
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
            phase = HeistGolem.Phase.TO_VAULT;
            waypointIndex = 0;
            currentTarget = null;
            return;
        }
        ItemStack taken = manager.tryTakeFromDock(golem.getTeam());
        if (taken != null) {
            golem.setCarried(taken);
            manager.updateLabel(golem);
            phase = HeistGolem.Phase.TO_VAULT;
            waypointIndex = 0;
            currentTarget = null;
        }
    }

    private void tickTravelling(List<Location> route, boolean toVault, double speed) {
        if (waypointIndex >= route.size()) {
            if (toVault) {
                manager.deposit(golem);
                phase = HeistGolem.Phase.TO_DOCK;
            } else {
                phase = HeistGolem.Phase.AT_DOCK;
            }
            waypointIndex = 0;
            currentTarget = null;
            return;
        }

        Location target = route.get(waypointIndex);
        Pathfinder pathfinder = golem.getEntity().getPathfinder();
        if (!target.equals(currentTarget)) {
            pathfinder.moveTo(target, speed);
            currentTarget = target;
        }

        if (golem.getEntity().getLocation().distanceSquared(target) <= arriveDistanceSquared()) {
            waypointIndex++;
            currentTarget = null;
        }
    }

    private void checkStuck() {
        Location now = golem.getEntity().getLocation();
        if (lastPosition == null || now.distanceSquared(lastPosition) > 1.0) {
            lastPosition = now;
            lastMovedMillis = System.currentTimeMillis();
            return;
        }
        if (System.currentTimeMillis() - lastMovedMillis > stuckMillis()) {
            List<Location> route = phase == HeistGolem.Phase.TO_DOCK
                    ? golem.getWaypointsToDock()
                    : golem.getWaypointsToVault();
            int index = Math.min(waypointIndex, route.size() - 1);
            if (index >= 0 && !route.isEmpty()) {
                golem.getEntity().teleport(route.get(index));
                currentTarget = null;
            }
            lastPosition = golem.getEntity().getLocation();
            lastMovedMillis = System.currentTimeMillis();
        }
    }
}
