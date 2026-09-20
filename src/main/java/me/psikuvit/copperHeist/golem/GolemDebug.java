package me.psikuvit.copperHeist.golem;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Golem debugging for playtests. Off by default and free when off (every call returns at once).
 *
 * <ul>
 * <li><b>Log</b> - /ch admin debug golems: what vanilla's golems do (animation state changes, what they pick up and put down,
 *     which chest was nearest) and what the plugin's guards do about it, tagged with the golem and category.</li>
 * <li><b>Verbose</b> - reserved for extra detail.</li>
 * <li><b>Visuals</b> - /ch admin debug visuals: the golem's label shows its state, a particle line runs from each golem to where
 *     vanilla is sending it, and every dock and vault chest is marked.</li>
 * <li><b>Dump</b> - /ch admin debug dump: a full snapshot of every golem plus the pending chest reservations.</li>
 * </ul>
 */
public class GolemDebug {

    public enum Category {
        STATE(NamedTextColor.AQUA), HAND(NamedTextColor.LIGHT_PURPLE), CHEST(NamedTextColor.GOLD), GUARD(NamedTextColor.RED);

        private final TextColor color;

        Category(TextColor color) {
            this.color = color;
        }
    }

    private final CopperHeist plugin;
    private final Set<UUID> subscribers = new HashSet<>();
    private boolean console;
    private boolean verbose;
    private boolean visuals;
    private BukkitTask visualTask;

    public GolemDebug(CopperHeist plugin) {
        this.plugin = plugin;
    }

    /** Cheap guard so callers can skip building a message when nobody is listening. */
    public boolean enabled() {
        return console || !subscribers.isEmpty();
    }

    public boolean verbose() {
        return verbose && enabled();
    }

    public boolean visuals() {
        return visuals;
    }

    public boolean subscribed(Object sender) {
        return sender instanceof Player player ? subscribers.contains(player.getUniqueId()) : console;
    }

    public void subscribe(Object sender, boolean on) {
        if (sender instanceof Player player) {
            if (on) subscribers.add(player.getUniqueId());
            else subscribers.remove(player.getUniqueId());
        } else {
            console = on;
        }
    }

    public void setVerbose(boolean on) {
        verbose = on;
    }

    public void setVisuals(boolean on) {
        visuals = on;
        if (on && visualTask == null) {
            visualTask = Bukkit.getScheduler().runTaskTimer(plugin, this::drawVisuals, 5L, 5L);
        } else if (!on && visualTask != null) {
            visualTask.cancel();
            visualTask = null;
        }
    }

    public void stop() {
        if (visualTask != null) visualTask.cancel();
        visualTask = null;
    }

    // ---- logging ----

    public void log(HeistGolem golem, Category category, String message) {
        if (!enabled()) return;
        send(category, "[" + golem.debugName() + "] " + message);
    }

    /** Something that isn't about one golem (a chest change, the work queue). */
    public void log(Category category, String message) {
        if (!enabled()) return;
        send(category, message);
    }

    private void send(Category category, String message) {
        String line = "[GolemDebug/" + category.name().toLowerCase(Locale.ROOT) + "] " + message;
        if (console) plugin.getLogger().info(line);
        Component text = Component.text(line, category.color);
        for (UUID id : subscribers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(text);
        }
    }

    public static String at(Location location) {
        if (location == null) return "none";
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f", location.getX(), location.getY(), location.getZ());
    }

    // ---- visuals ----

    private void drawVisuals() {
        if (subscribers.isEmpty()) return;
        for (var game : plugin.getGameManager().all()) {
            for (HeistGolem golem : game.getGolemManager().all()) {
                Location from = golem.getEntity().getLocation().add(0, 0.6, 0);
                Location target = game.getGolemManager().debugTarget(golem);
                if (target != null && target.getWorld() == from.getWorld()) {
                    line(from, target.clone().add(0.5, 0.3, 0.5));
                }
            }
            for (var team : Team.values()) {
                for (Location dock : game.getArena().site(team).dockChests) show(dock.clone().add(0.5, 1.3, 0.5), Particle.HAPPY_VILLAGER, 2);
                for (Location vault : game.getArena().site(team).vaultChests) show(vault.clone().add(0.5, 1.3, 0.5), Particle.WAX_ON, 2);
            }
        }
    }

    private void line(Location from, Location to) {
        double distance = from.distance(to);
        int steps = (int) Math.min(40, Math.max(1, distance / 0.6));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Location point = from.clone().add(to.clone().subtract(from).toVector().multiply(t));
            show(point, Particle.END_ROD, 1);
        }
    }

    private void show(Location at, Particle particle, int count) {
        for (UUID id : subscribers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.getWorld().equals(at.getWorld())) player.spawnParticle(particle, at, count, 0, 0, 0, 0);
        }
    }
}
