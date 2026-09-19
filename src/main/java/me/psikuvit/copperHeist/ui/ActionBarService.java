package me.psikuvit.copperHeist.ui;

import me.psikuvit.copperHeist.CopperHeist;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One owner for every player's action bar, so messages stop overwriting each other.
 *
 * <ul>
 * <li><b>Messages</b> ({@link #show}, {@link #important}) have a priority and a duration. While one is active, anything
 *     with a lower priority is ignored and the ambient bar stays hidden; an equal or higher priority replaces it.</li>
 * <li>The <b>ambient bar</b> ({@link #ambient}) is the looped status line (what you carry, ability state). Whoever owns it
 *     keeps calling {@link #ambient}; it lasts a couple of seconds, so it vanishes on its own when they stop.
 *     It is shown whenever no message is active.</li>
 * </ul>
 * The winner is re-sent every second so it doesn't fade out client-side.
 */
public class ActionBarService {

    public enum Priority {
        NORMAL, HIGH, CRITICAL
    }

    private record Message(Component text, Priority priority, long expiresAtTick) {
    }

    private record Ambient(Component text, long expiresAtTick) {
    }

    private static final int AMBIENT_TICKS = 50;
    private static final int RESEND_TICKS = 20;

    private final CopperHeist plugin;
    private final Map<UUID, Message> messages = new HashMap<>();
    private final Map<UUID, Ambient> ambients = new HashMap<>();
    private final Map<UUID, Component> lastSent = new HashMap<>();
    private final Map<UUID, Long> lastSentTick = new HashMap<>();
    private BukkitTask task;

    public ActionBarService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void stop() {
        if (task != null) task.cancel();
        messages.clear();
        ambients.clear();
        lastSent.clear();
        lastSentTick.clear();
    }

    /** A normal message for actionbar.message-seconds (default 2). */
    public void show(Player player, Component text) {
        show(player, text, Priority.NORMAL, plugin.settings().getInt("actionbar.message-seconds", 2) * 20);
    }

    /** A message that beats normal ones (progress bars, respawn countdown). */
    public void important(Player player, Component text) {
        show(player, text, Priority.HIGH, Math.max(20, plugin.settings().getInt("actionbar.message-seconds", 2) * 20));
    }

    public void show(Player player, Component text, Priority priority, int durationTicks) {
        long now = now();
        Message current = messages.get(player.getUniqueId());
        if (current != null && current.expiresAtTick() > now && current.priority().compareTo(priority) > 0) return;
        messages.put(player.getUniqueId(), new Message(text, priority, now + durationTicks));
        send(player, text, now);
    }

    /** Sets the looped status line. Call it again to keep it alive; it expires after about 2.5 seconds. */
    public void ambient(Player player, Component text) {
        long now = now();
        ambients.put(player.getUniqueId(), new Ambient(text, now + AMBIENT_TICKS));
        Message current = messages.get(player.getUniqueId());
        if (current == null || current.expiresAtTick() <= now) send(player, text, now);
    }

    public void clear(Player player) {
        UUID id = player.getUniqueId();
        messages.remove(id);
        ambients.remove(id);
        lastSent.remove(id);
        lastSentTick.remove(id);
    }

    private void tick() {
        long now = now();
        messages.values().removeIf(message -> message.expiresAtTick() <= now);
        ambients.values().removeIf(ambient -> ambient.expiresAtTick() <= now);

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Message message = messages.get(id);
            Ambient ambient = ambients.get(id);
            Component wanted = message != null ? message.text() : ambient != null ? ambient.text() : null;
            Component sent = lastSent.get(id);
            if (wanted == null) {
                if (sent != null) {
                    player.sendActionBar(Component.empty());
                    lastSent.remove(id);
                }
                continue;
            }
            long since = now - lastSentTick.getOrDefault(id, 0L);
            if (!wanted.equals(sent) || since >= RESEND_TICKS) send(player, wanted, now);
        }
    }

    private void send(Player player, Component text, long now) {
        player.sendActionBar(text);
        lastSent.put(player.getUniqueId(), text);
        lastSentTick.put(player.getUniqueId(), now);
    }

    private static long now() {
        return Bukkit.getCurrentTick();
    }
}
