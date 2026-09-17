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

    private static final double ARRIVE_DISTANCE_SQUARED = 2.25; // 1.5 blocks
    private static final long STUCK_MILLIS = 8000;

    private final HeistGolem golem;
    private final GolemManager manager;

    private HeistGolem.Phase phase = HeistGolem.Phase.AT_DOCK;
    private int waypointIndex = 0;
    private Location currentTarget;
    private Location lastPosition;
    private long lastMovedMillis = System.currentTimeMillis();

    public GolemBrain(HeistGolem golem, GolemManager manager) {
        this.golem = golem;
        this.manager = manager;
    }

    public boolean shouldActivate() {
        return !golem.getEntity().isDead();
    }

    public void start() {
        lastPosition = golem.getEntity().getLocation();
        lastMovedMillis = System.currentTimeMillis();
    }

    public void tick() {
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

        if (golem.getEntity().getLocation().distanceSquared(target) <= ARRIVE_DISTANCE_SQUARED) {
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
        if (System.currentTimeMillis() - lastMovedMillis > STUCK_MILLIS) {
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
