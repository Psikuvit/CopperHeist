package me.psikuvit.copperHeist.heist;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.event.AlarmDestroyedEvent;
import me.psikuvit.copperHeist.event.AlarmTriggeredEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.task.heist.AlarmScanTask;
import me.psikuvit.copperHeist.util.Cooldowns;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Team-placed alarms: an invisible {@link Interaction} hitbox near a team's
 * spawn that glows and announces any enemy who walks close, unless they're
 * sneaking (mirrors vanilla sculk sensors). No base-region concept exists in
 * this build, so "own base" is adapted to a radius around the team spawn,
 * the same adaptation {@code RoleService} already uses for Guard's bonus.
 */
public class AlarmManager {

    private final CopperHeist plugin;
    private final Game game;
    private final Map<Team, List<Alarm>> alarms = new EnumMap<>(Team.class);
    private final Cooldowns triggerCooldowns = new Cooldowns();
    private BukkitTask task;

    public AlarmManager(CopperHeist plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
        alarms.put(Team.COPPER, new ArrayList<>());
        alarms.put(Team.IRON, new ArrayList<>());
    }

    public void start() {
        task = new AlarmScanTask(this).runTaskTimer(plugin, 10L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (List<Alarm> teamAlarms : alarms.values()) {
            for (Alarm alarm : teamAlarms) {
                plugin.getGameManager().unregisterHeistEntity(alarm.hitbox().getUniqueId());
                alarm.hitbox().remove();
                removeBox(alarm);
            }
            teamAlarms.clear();
        }
    }

    public int capFor(Team team) {
        int base = game.settings().getInt("alarms.base-per-team", 4);
        return base + game.getRoleService().extraAlarms(team);
    }

    public int countFor(Team team) {
        return alarms.get(team).size();
    }

    public boolean isWithinPlacementRange(Team team, Location loc) {
        return game.isInBase(team, loc, game.settings().getDouble("alarms.placement-radius", 20));
    }

    public Alarm place(Team team, Location loc) {
        if (alarms.get(team).size() >= capFor(team)) return null;

        Interaction hitbox = loc.getWorld().spawn(loc.clone().add(0.5, 0, 0.5), Interaction.class, entity -> {
            entity.setInteractionWidth(0.8f);
            entity.setInteractionHeight(0.8f);
            });
        plugin.getGameManager().registerHeistEntity(game, hitbox.getUniqueId());

        Alarm alarm = new Alarm(team, loc, hitbox, spawnBox(team, loc));
        alarms.get(team).add(alarm);
        showBox(alarm);
        return alarm;
    }

    /** A small glowing cube on the alarm, hidden from everyone by default; only the owning team is shown it. */
    private BlockDisplay spawnBox(Team team, Location loc) {
        if (!game.settings().getBoolean("alarms.show-box", true)) return null;
        Location at = loc.clone().add(0.5 - 0.2, 0, 0.5 - 0.2);
        BlockDisplay box = loc.getWorld().spawn(at, BlockDisplay.class, entity -> {
            entity.setBlock(Material.TRIPWIRE_HOOK.createBlockData());
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.4f, 0.4f, 0.4f), new AxisAngle4f()));
            entity.setGlowing(true);
            entity.setGlowColorOverride(team.armorColor());
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
        });
        plugin.getGameManager().registerHeistEntity(game, box.getUniqueId());
        return box;
    }

    private void showBox(Alarm alarm) {
        if (alarm.box() == null) return;
        for (Player player : game.onlinePlayers()) {
            GamePlayer gp = game.getGamePlayer(player.getUniqueId());
            if (gp != null && gp.getTeam() == alarm.team()) player.showEntity(plugin, alarm.box());
        }
    }

    private void removeBox(Alarm alarm) {
        if (alarm.box() == null) return;
        plugin.getGameManager().unregisterHeistEntity(alarm.box().getUniqueId());
        alarm.box().remove();
    }

    public Alarm findByEntity(UUID entityId) {
        for (List<Alarm> teamAlarms : alarms.values()) {
            for (Alarm alarm : teamAlarms) {
                if (alarm.hitbox().getUniqueId().equals(entityId)) return alarm;
            }
        }
        return null;
    }

    public void destroy(Alarm alarm, Player destroyer) {
        alarms.get(alarm.team()).remove(alarm);
        plugin.getGameManager().unregisterHeistEntity(alarm.hitbox().getUniqueId());
        alarm.hitbox().remove();
        removeBox(alarm);
        Bukkit.getPluginManager().callEvent(new AlarmDestroyedEvent(game, alarm, destroyer));
    }

    public void tick() {
        if (!game.isActive()) return;
        double radius = game.settings().getDouble("alarms.trigger-radius", 4);

        for (Team team : Team.values()) {
            for (Alarm alarm : alarms.get(team)) {
                showBox(alarm); // covers teammates who joined or reconnected after it was placed
                if (!triggerCooldowns.isReady(alarm.hitbox().getUniqueId())) continue;
                Player intruder = findIntruder(alarm, team, radius);
                if (intruder != null) trigger(alarm, intruder);
            }
        }
    }

    private Player findIntruder(Alarm alarm, Team owningTeam, double radius) {
        Location loc = alarm.location();
        if (loc.getWorld() == null) return null;
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.isSneaking()) continue;
            GamePlayer gp = game.getGamePlayer(player.getUniqueId());
            if (gp == null || gp.getTeam() == owningTeam) continue;
            if (player.getLocation().distanceSquared(loc) <= radius * radius) return player;
        }
        return null;
    }

    private void trigger(Alarm alarm, Player intruder) {
        int cooldown = game.settings().getInt("alarms.cooldown-seconds", 8);
        triggerCooldowns.set(alarm.hitbox().getUniqueId(), cooldown);

        int glowSeconds = game.settings().getInt("alarms.intruder-glow-seconds", 3);
        intruder.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowSeconds * 20, 0, false, false));
        intruder.getWorld().playSound(alarm.location(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.0f, 0.8f);

        Bukkit.getPluginManager().callEvent(new AlarmTriggeredEvent(game, alarm, intruder));
    }
}
