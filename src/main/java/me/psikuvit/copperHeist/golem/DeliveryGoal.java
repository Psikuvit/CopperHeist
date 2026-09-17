package me.psikuvit.copperHeist.golem;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.List;

/**
 * Custom Mob Goal driving a copper golem's dock -> vault -> dock loop.
 * Vanilla goals are removed from match golems (doc §5.2) since the built-in
 * item-sorting AI isn't predictable enough for a competitive corridor.
 */
public class DeliveryGoal implements Goal<Mob> {

    private static GoalKey<Mob> key;

    public static void init(Plugin plugin) {
        key = GoalKey.of(Mob.class, new NamespacedKey(plugin, "delivery"));
    }

    public static GoalKey<Mob> key() {
        return key;
    }

    private static final double ARRIVE_DISTANCE_SQUARED = 2.25; // 1.5 blocks
    private static final long STUCK_MILLIS = 8000;

    private final HeistGolem golem;
    private final GolemManager manager;

    public DeliveryGoal(HeistGolem golem, GolemManager manager) {
        this.golem = golem;
        this.manager = manager;
    }

    @Override
    public boolean shouldActivate() {
        return !golem.getEntity().isDead();
    }

    @Override
    public void start() {
        golem.setLastPosition(golem.getEntity().getLocation());
        golem.setLastMovedMillis(System.currentTimeMillis());
    }

    @Override
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

        switch (golem.getPhase()) {
            case AT_DOCK -> tickAtDock();
            case TO_VAULT -> tickTravelling(golem.getWaypointsToVault(), true, speed);
            case TO_DOCK -> tickTravelling(golem.getWaypointsToDock(), false, speed);
        }

        checkStuck();
    }

    private void tickAtDock() {
        if (golem.isCarrying()) {
            golem.setPhase(HeistGolem.Phase.TO_VAULT);
            golem.setWaypointIndex(0);
            golem.setCurrentTarget(null);
            return;
        }
        ItemStack taken = manager.tryTakeFromDock(golem.getTeam());
        if (taken != null) {
            golem.setCarried(taken);
            manager.updateLabel(golem);
            golem.setPhase(HeistGolem.Phase.TO_VAULT);
            golem.setWaypointIndex(0);
            golem.setCurrentTarget(null);
        }
    }

    private void tickTravelling(List<Location> route, boolean toVault, double speed) {
        int index = golem.getWaypointIndex();
        if (index >= route.size()) {
            if (toVault) {
                manager.deposit(golem);
                golem.setPhase(HeistGolem.Phase.TO_DOCK);
            } else {
                golem.setPhase(HeistGolem.Phase.AT_DOCK);
            }
            golem.setWaypointIndex(0);
            golem.setCurrentTarget(null);
            return;
        }

        Location target = route.get(index);
        Pathfinder pathfinder = golem.getEntity().getPathfinder();
        if (!target.equals(golem.getCurrentTarget())) {
            pathfinder.moveTo(target, speed);
            golem.setCurrentTarget(target);
        }

        if (golem.getEntity().getLocation().distanceSquared(target) <= ARRIVE_DISTANCE_SQUARED) {
            golem.setWaypointIndex(index + 1);
            golem.setCurrentTarget(null);
        }
    }

    private void checkStuck() {
        Location now = golem.getEntity().getLocation();
        Location last = golem.getLastPosition();
        if (last == null || now.distanceSquared(last) > 1.0) {
            golem.setLastPosition(now);
            golem.setLastMovedMillis(System.currentTimeMillis());
            return;
        }
        if (System.currentTimeMillis() - golem.getLastMovedMillis() > STUCK_MILLIS) {
            List<Location> route = golem.getPhase() == HeistGolem.Phase.TO_DOCK
                    ? golem.getWaypointsToDock()
                    : golem.getWaypointsToVault();
            int index = Math.min(golem.getWaypointIndex(), route.size() - 1);
            if (index >= 0 && !route.isEmpty()) {
                golem.getEntity().teleport(route.get(index));
                golem.setCurrentTarget(null);
            }
            golem.setLastPosition(golem.getEntity().getLocation());
            golem.setLastMovedMillis(System.currentTimeMillis());
        }
    }

    @Override
    public @NonNull GoalKey<Mob> getKey() {
        return key;
    }

    @Override
    public @NonNull EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE);
    }
}
