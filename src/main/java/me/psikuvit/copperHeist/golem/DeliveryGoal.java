package me.psikuvit.copperHeist.golem;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;

/**
 * Thin Paper Mob Goal adapter - registered with Bukkit's MobGoals API so the
 * server actually ticks it, but every real decision is delegated to the
 * golem's {@link GolemBrain}. Vanilla goals are removed from match golems
 * (doc §5.2) since the built-in item-sorting AI isn't predictable enough for
 * a competitive corridor.
 */
public class DeliveryGoal implements Goal<Mob> {

    private static GoalKey<Mob> key;

    public static void init(Plugin plugin) {
        key = GoalKey.of(Mob.class, new NamespacedKey(plugin, "delivery"));
    }

    public static GoalKey<Mob> key() {
        return key;
    }

    private final HeistGolem golem;

    public DeliveryGoal(HeistGolem golem) {
        this.golem = golem;
    }

    @Override
    public boolean shouldActivate() {
        return golem.getBrain().shouldActivate();
    }

    @Override
    public void start() {
        golem.getBrain().start();
    }

    @Override
    public void tick() {
        golem.getBrain().tick();
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
