package me.psikuvit.copperHeist.golem;

import me.psikuvit.copperHeist.config.Settings;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;

/** The sounds and particles that make a golem's work readable. Each kind can be switched off in golems.animation. */
public final class GolemEffects {

    private final Settings settings;

    public GolemEffects(Settings settings) {
        this.settings = settings;
    }

    public boolean enabled() {
        return settings.getBoolean("golems.animation.enabled", true);
    }

    public void pickedUp(Location at) {
        sound(at, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.9f);
        particles(at, Particle.POOF, 6, 0.3);
    }

    public void deposited(Location at) {
        sound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        particles(at, Particle.HAPPY_VILLAGER, 8, 0.4);
    }

    public void stunned(Location at) {
        sound(at, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.0f, 1.3f);
        particles(at, Particle.CRIT, 10, 0.4);
    }

    /** Called every so often while a golem is stunned - little sparks over its head. */
    public void dizzy(Location at) {
        particles(at.clone().add(0, 1.2, 0), Particle.ELECTRIC_SPARK, 4, 0.25);
    }

    public void scraped(Location at) {
        sound(at, Sound.ITEM_AXE_SCRAPE, 1.0f, 1.0f);
        particles(at.clone().add(0, 0.8, 0), Particle.SCRAPE, 12, 0.4);
    }

    public void waxed(Location at) {
        sound(at, Sound.ITEM_HONEYCOMB_WAX_ON, 1.0f, 1.0f);
        particles(at.clone().add(0, 0.8, 0), Particle.WAX_ON, 12, 0.4);
    }

    private void sound(Location at, Sound sound, float volume, float pitch) {
        if (!enabled() || !settings.getBoolean("golems.animation.sounds", true)) return;
        World world = at.getWorld();
        if (world != null) world.playSound(at, sound, volume, pitch);
    }

    private void particles(Location at, Particle particle, int count, double spread) {
        if (!enabled() || !settings.getBoolean("golems.animation.particles", true)) return;
        World world = at.getWorld();
        if (world != null) world.spawnParticle(particle, at.clone().add(0, 0.8, 0), count, spread, spread, spread, 0.02);
    }
}
