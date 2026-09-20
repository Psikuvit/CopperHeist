package me.psikuvit.copperHeist.cosmetics.effect;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.EffectContext;
import me.psikuvit.copperHeist.cosmetics.EffectProvider;
import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A particle trail behind an arrow, wind charge, trident or any other projectile ({@code effect: particle-trail}). Params are those of
 * {@link ParticleSpec}. Each trail is one small task that ends when the projectile lands or vanishes (or after {@code cosmetics.trail-max-ticks});
 * at most {@code cosmetics.max-active-trails} run at once server-wide, so a crowded match can't turn trails into lag - extra shots simply get none.
 * In a menu preview (no projectile) it draws a short line in front of the player.
 */
public class ParticleTrailEffect implements EffectProvider {

    private final CopperHeist plugin;
    private final AtomicInteger active = new AtomicInteger();

    public ParticleTrailEffect(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "particle-trail";
    }

    @Override
    public Set<CosmeticCategory> categories() {
        return Set.of(CosmeticCategory.TRAIL);
    }

    @Override
    public void validate(CosmeticDefinition cosmetic) {
        ParticleSpec.parse(cosmetic.params());
    }

    @Override
    public void play(EffectContext context) {
        ParticleSpec spec = ParticleSpec.parse(context.cosmetic().params());
        if (context.entity() == null) {
            preview(context, spec);
            return;
        }
        int max = plugin.settings().getInt("cosmetics.max-active-trails", 48);
        if (active.get() >= max) return;
        active.incrementAndGet();
        int maxTicks = plugin.settings().getInt("cosmetics.trail-max-ticks", 160);
        new Follow(context.entity(), spec, new ArrayList<>(context.viewers()), maxTicks).runTaskTimer(plugin, 1L, 1L);
    }

    /** A brief trail in front of the previewing player, so they can see what it looks like. */
    private void preview(EffectContext context, ParticleSpec spec) {
        Player owner = context.owner();
        Location start = owner.getEyeLocation().add(owner.getLocation().getDirection().multiply(1.0));
        Vector step = owner.getLocation().getDirection().multiply(0.6);
        Collection<Player> viewers = context.viewers();
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick >= 24 || !owner.isOnline()) {
                    cancel();
                    return;
                }
                spec.send(viewers, start.clone().add(step.clone().multiply(tick)), tick);
                tick++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private final class Follow extends BukkitRunnable {

        private final Entity entity;
        private final ParticleSpec spec;
        private final List<Player> viewers;
        private final int maxTicks;
        private int age;

        private Follow(Entity entity, ParticleSpec spec, List<Player> viewers, int maxTicks) {
            this.entity = entity;
            this.spec = spec;
            this.viewers = viewers;
            this.maxTicks = maxTicks;
        }

        @Override
        public void run() {
            boolean landed = entity instanceof AbstractArrow arrow ? arrow.isInBlock() : entity.isOnGround();
            if (!entity.isValid() || landed || age++ >= maxTicks) {
                cancel();
                return;
            }
            spec.send(viewers, entity.getLocation(), age);
        }

        @Override
        public synchronized void cancel() throws IllegalStateException {
            super.cancel();
            active.decrementAndGet();
        }
    }
}
