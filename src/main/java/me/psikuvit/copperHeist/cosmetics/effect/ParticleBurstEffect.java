package me.psikuvit.copperHeist.cosmetics.effect;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.EffectContext;
import me.psikuvit.copperHeist.cosmetics.EffectProvider;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A shape of particles at a spot ({@code effect: particle-burst}) - for kill effects, victory effects and join effects. Params are those of
 * {@link ParticleSpec} plus:
 * <pre>
 *   shape: burst    burst (one puff, uses count/spread/speed) | ring | sphere | rain | spiral (rises over one second)
 *   radius: 1.5     size of the ring, sphere, rain or spiral (0.5-5)
 *   height: 2.0     how high the rain starts / the spiral climbs (0.5-6)
 * </pre>
 */
public class ParticleBurstEffect implements EffectProvider {

    private static final Set<String> SHAPES = Set.of("burst", "ring", "sphere", "rain", "spiral");
    private static final int SPIRAL_TICKS = 20;

    private final CopperHeist plugin;

    public ParticleBurstEffect(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "particle-burst";
    }

    @Override
    public Set<CosmeticCategory> categories() {
        return Set.of(CosmeticCategory.KILL_EFFECT, CosmeticCategory.VICTORY, CosmeticCategory.JOIN, CosmeticCategory.GOLEM);
    }

    @Override
    public void validate(CosmeticDefinition cosmetic) {
        ParticleSpec.parse(cosmetic.params());
        String shape = shape(cosmetic.params());
        if (!SHAPES.contains(shape)) throw new IllegalArgumentException("unknown shape '" + shape + "' (use " + String.join(", ", SHAPES) + ")");
    }

    @Override
    public void play(EffectContext context) {
        Map<String, Object> params = context.cosmetic().params();
        ParticleSpec spec = ParticleSpec.parse(params);
        String shape = shape(params);
        double radius = ParticleSpec.clamp(ParticleSpec.number(params, "radius", 1.5), 0.5, 5);
        double height = ParticleSpec.clamp(ParticleSpec.number(params, "height", 2.0), 0.5, 6);
        Location center = context.location();
        Collection<Player> viewers = context.viewers();

        if (shape.equals("spiral")) {
            new BukkitRunnable() {
                private int tick;

                @Override
                public void run() {
                    if (tick >= SPIRAL_TICKS) {
                        cancel();
                        return;
                    }
                    for (Vector offset : points("spiral", radius, height, tick)) spec.send(viewers, center.clone().add(offset), tick);
                    tick++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
            return;
        }
        for (Vector offset : points(shape, radius, height, 0)) spec.send(viewers, center.clone().add(offset), 0);
    }

    private static String shape(Map<String, Object> params) {
        return String.valueOf(params.getOrDefault("shape", "burst")).toLowerCase(Locale.ROOT);
    }

    /** Where the particles go, relative to the centre. For "spiral" it is the two points of one tick of the climb. */
    static List<Vector> points(String shape, double radius, double height, int tick) {
        List<Vector> points = new ArrayList<>();
        switch (shape) {
            case "ring" -> {
                for (int i = 0; i < 24; i++) {
                    double angle = 2 * Math.PI * i / 24;
                    points.add(new Vector(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius));
                }
            }
            case "sphere" -> {
                int count = 40;
                double golden = Math.PI * (3 - Math.sqrt(5));
                for (int i = 0; i < count; i++) {
                    double y = 1 - (2.0 * i) / (count - 1);
                    double ring = Math.sqrt(1 - y * y);
                    double angle = golden * i;
                    points.add(new Vector(Math.cos(angle) * ring * radius, 1 + y * radius, Math.sin(angle) * ring * radius));
                }
            }
            case "rain" -> {
                Random random = new Random(tick * 31L + 7);
                for (int i = 0; i < 14; i++) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double distance = Math.sqrt(random.nextDouble()) * radius;
                    points.add(new Vector(Math.cos(angle) * distance, height, Math.sin(angle) * distance));
                }
            }
            case "spiral" -> {
                double climb = height * tick / SPIRAL_TICKS;
                for (int strand = 0; strand < 2; strand++) {
                    double angle = tick * 0.55 + strand * Math.PI;
                    points.add(new Vector(Math.cos(angle) * radius, climb, Math.sin(angle) * radius));
                }
            }
            default -> points.add(new Vector(0, 1, 0));
        }
        return points;
    }
}
