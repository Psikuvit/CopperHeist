package me.psikuvit.copperHeist.heist;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.event.VaultDrillCompletedEvent;
import me.psikuvit.copperHeist.event.VaultDrillDestroyedEvent;
import me.psikuvit.copperHeist.event.VaultDrillPlacedEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.task.VaultDrillTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * One active Vault Drill per team's vault at a time: a BlockDisplay +
 * progress TextDisplay + {@link Interaction} hitbox placed on the enemy
 * vault door. Progress only advances while an attacker stands nearby;
 * defenders damage it by hitting the hitbox. On completion the target
 * vault opens for a timed window - {@link #isBreached} is what
 * ArenaProtectionListener checks before letting an enemy open a vault chest.
 */
public class VaultDrillManager {

    private final CopperHeist plugin;
    private final Game game;
    private final Map<Team, VaultDrill> activeDrills = new EnumMap<>(Team.class);
    private final Map<Team, Long> cooldownExpiryMillis = new EnumMap<>(Team.class);
    private final Map<Team, Long> breachOpenUntilMillis = new EnumMap<>(Team.class);
    private final Map<Team, BossBar> bossBars = new EnumMap<>(Team.class);
    private BukkitTask task;

    public VaultDrillManager(CopperHeist plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    public void start() {
        task = new VaultDrillTask(this).runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (VaultDrill drill : activeDrills.values()) removeVisuals(drill);
        activeDrills.clear();
        for (BossBar bar : bossBars.values()) {
            for (Player player : game.onlinePlayers()) player.hideBossBar(bar);
        }
        bossBars.clear();
    }

    public boolean isActive(Team defenderTeam) {
        return activeDrills.containsKey(defenderTeam);
    }

    public long cooldownRemainingSeconds(Team attackerTeam) {
        Long expiry = cooldownExpiryMillis.get(attackerTeam);
        if (expiry == null) return 0;
        return Math.max(0, (expiry - System.currentTimeMillis()) / 1000);
    }

    public boolean isBreached(Team defenderTeam) {
        Long until = breachOpenUntilMillis.get(defenderTeam);
        return until != null && System.currentTimeMillis() < until;
    }

    public VaultDrill place(Team attackerTeam, UUID placer) {
        Team defenderTeam = attackerTeam.opposite();
        if (isActive(defenderTeam) || cooldownRemainingSeconds(attackerTeam) > 0) return null;

        Arena.TeamSite site = game.getArena().site(defenderTeam);
        Location door = site.vaultDoor;
        if (door == null || door.getWorld() == null) return null;

        Location loc = door.clone().add(0.5, 0, 0.5);
        BlockDisplay display = loc.getWorld().spawn(loc, BlockDisplay.class, entity -> {
            entity.setBlock(Material.IRON_BLOCK.createBlockData());
            entity.setPersistent(true);
        });
        TextDisplay label = loc.getWorld().spawn(loc.clone().add(0, 1.5, 0), TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(true);
        });
        Interaction hitbox = loc.getWorld().spawn(loc.clone(), Interaction.class, entity -> {
            entity.setInteractionWidth(1.0f);
            entity.setInteractionHeight(2.0f);
            entity.setPersistent(true);
        });
        plugin.getGameManager().registerHeistEntity(game, hitbox.getUniqueId());

        double maxHealth = game.settings().getDouble("drill.health", 40.0);
        VaultDrill drill = new VaultDrill(attackerTeam, defenderTeam, display, label, hitbox, maxHealth, placer);
        activeDrills.put(defenderTeam, drill);
        updateVisuals(drill);

        BossBar bar = BossBar.bossBar(progressText(drill), 0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        bossBars.put(defenderTeam, bar);
        for (Player player : game.onlinePlayers()) player.showBossBar(bar);

        Bukkit.getPluginManager().callEvent(new VaultDrillPlacedEvent(game, drill));
        return drill;
    }

    public VaultDrill findByEntity(UUID entityId) {
        for (VaultDrill drill : activeDrills.values()) {
            if (drill.getHitbox().getUniqueId().equals(entityId)) return drill;
        }
        return null;
    }

    public void onHit(VaultDrill drill, Player defender) {
        GamePlayer gp = game.getGamePlayer(defender.getUniqueId());
        if (gp == null || gp.getTeam() != drill.getDefender()) return;

        double damage = game.settings().getDouble("drill.hit-damage", 5.0);
        drill.damage(damage);
        if (drill.isDestroyed()) {
            destroy(drill, defender);
        } else {
            updateVisuals(drill);
        }
    }

    public void tick() {
        if (!game.isActive()) return;
        double radiusSquared = Math.pow(game.settings().getDouble("drill.attacker-radius", 6), 2);
        double duration = game.settings().getInt("drill.duration-seconds", 30);

        for (VaultDrill drill : new ArrayList<>(activeDrills.values())) {
            if (hasNearbyAttacker(drill, radiusSquared)) {
                drill.addProgress(1.0);
                updateVisuals(drill);
                if (drill.getProgressSeconds() >= duration) complete(drill);
            }
        }
    }

    private boolean hasNearbyAttacker(VaultDrill drill, double radiusSquared) {
        Location loc = drill.getHitbox().getLocation();
        for (Player player : loc.getWorld().getPlayers()) {
            GamePlayer gp = game.getGamePlayer(player.getUniqueId());
            if (gp == null || gp.getTeam() != drill.getAttacker()) continue;
            if (player.getLocation().distanceSquared(loc) <= radiusSquared) return true;
        }
        return false;
    }

    private void complete(VaultDrill drill) {
        Team defenderTeam = drill.getDefender();
        activeDrills.remove(defenderTeam);
        removeVisuals(drill);
        hideBossBar(defenderTeam);

        int breachSeconds = game.settings().getInt("drill.breach-open-seconds", 20);
        breachOpenUntilMillis.put(defenderTeam, System.currentTimeMillis() + breachSeconds * 1000L);
        int cooldownSeconds = game.settings().getInt("drill.cooldown-seconds", 90);
        cooldownExpiryMillis.put(drill.getAttacker(), System.currentTimeMillis() + cooldownSeconds * 1000L);

        GamePlayer placerGp = game.getGamePlayer(drill.getPlacer());
        if (placerGp != null) placerGp.addDrillCompleted();

        Bukkit.getPluginManager().callEvent(new VaultDrillCompletedEvent(game, drill, breachSeconds));
    }

    private void destroy(VaultDrill drill, Player destroyer) {
        activeDrills.remove(drill.getDefender());
        removeVisuals(drill);
        hideBossBar(drill.getDefender());
        GamePlayer destroyerGp = game.getGamePlayer(destroyer.getUniqueId());
        if (destroyerGp != null) destroyerGp.addDrillDestroyed();
        Bukkit.getPluginManager().callEvent(new VaultDrillDestroyedEvent(game, drill, destroyer));
    }

    private void hideBossBar(Team defenderTeam) {
        BossBar bar = bossBars.remove(defenderTeam);
        if (bar == null) return;
        for (Player player : game.onlinePlayers()) player.hideBossBar(bar);
    }

    private void updateVisuals(VaultDrill drill) {
        drill.getProgressLabel().text(Component.text(
                (int) (drill.progressFraction(game.settings().getInt("drill.duration-seconds", 30)) * 100) + "%  "
                        + (int) drill.getHealth() + "hp", NamedTextColor.RED));

        BossBar bar = bossBars.get(drill.getDefender());
        if (bar != null) {
            bar.progress((float) drill.progressFraction(game.settings().getInt("drill.duration-seconds", 30)));
            bar.name(progressText(drill));
        }
    }

    private Component progressText(VaultDrill drill) {
        int percent = (int) (drill.progressFraction(game.settings().getInt("drill.duration-seconds", 30)) * 100);
        return plugin.getMessageService().get("drill.progress", "target", drill.getDefender().displayName(), "percent", percent);
    }

    private void removeVisuals(VaultDrill drill) {
        plugin.getGameManager().unregisterHeistEntity(drill.getHitbox().getUniqueId());
        drill.getDisplay().remove();
        drill.getProgressLabel().remove();
        drill.getHitbox().remove();
    }
}
