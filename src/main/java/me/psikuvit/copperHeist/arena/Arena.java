package me.psikuvit.copperHeist.arena;

import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.ui.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class Arena {

    /** Where a loot point sits: common central loot, contested rare spots (diamonds), or slower side caches. */
    public enum LootZone {
        COMMON, RARE, CACHE
    }

    public static class TeamSite {
        public Location spawn;
        public final List<Location> dockChests = new ArrayList<>();
        public final List<Location> vaultChests = new ArrayList<>();
        public Location golemIdle;
        public final List<Location> waypoints = new ArrayList<>();
        public Location vaultDoor;
        public Location shop;
        public Location baseCorner1;
        public Location baseCorner2;
        public Location vaultCorner1;
        public Location vaultCorner2;

        /** The team's base area, or null if both corners haven't been set (callers then fall back to a radius around the spawn). */
        public Region base() {
            return baseCorner1 == null || baseCorner2 == null ? null : Region.of(baseCorner1, baseCorner2);
        }

        /** The sealed vault room, or null if not set. */
        public Region vaultRegion() {
            return vaultCorner1 == null || vaultCorner2 == null ? null : Region.of(vaultCorner1, vaultCorner2);
        }
    }

    /** A block that launches whoever steps on it; power scales both the lift and the forward push. */
    public record GustPad(Location location, double power) {
    }

    private final String name;
    private String worldName;
    private boolean enabled = false;
    private Location lobby;
    private Location spectator;
    private Location bound1;
    private Location bound2;
    private final Map<Team, TeamSite> sites = new EnumMap<>(Team.class);
    private final Map<LootZone, List<Location>> lootZones = new EnumMap<>(LootZone.class);
    private final List<Location> relicPoints = new ArrayList<>();
    private final List<GustPad> gustPads = new ArrayList<>();

    public Arena(String name) {
        this.name = name;
        for (LootZone zone : LootZone.values()) lootZones.put(zone, new ArrayList<>());
        sites.put(Team.COPPER, new TeamSite());
        sites.put(Team.IRON, new TeamSite());
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public World getWorld() {
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Location getLobby() {
        return lobby;
    }

    public void setLobby(Location lobby) {
        this.lobby = lobby;
    }

    public Location getSpectator() {
        return spectator;
    }

    public void setSpectator(Location spectator) {
        this.spectator = spectator;
    }

    public Location getBound1() {
        return bound1;
    }

    public void setBound1(Location bound1) {
        this.bound1 = bound1;
    }

    public Location getBound2() {
        return bound2;
    }

    public void setBound2(Location bound2) {
        this.bound2 = bound2;
    }

    public TeamSite site(Team team) {
        return sites.get(team);
    }

    /** Common central-zone points - where most loot spawns. */
    public List<Location> getLootPoints() {
        return lootZones.get(LootZone.COMMON);
    }

    public List<Location> getLootPoints(LootZone zone) {
        return lootZones.get(zone);
    }

    public List<Location> allLootPoints() {
        List<Location> all = new ArrayList<>();
        for (List<Location> points : lootZones.values()) all.addAll(points);
        return all;
    }

    public List<GustPad> getGustPads() {
        return gustPads;
    }

    public List<Location> getRelicPoints() {
        return relicPoints;
    }

    public boolean isInBounds(Location loc) {
        if (bound1 == null || bound2 == null || loc.getWorld() == null) return true;
        if (!loc.getWorld().equals(bound1.getWorld())) return false;
        double minX = Math.min(bound1.getX(), bound2.getX());
        double maxX = Math.max(bound1.getX(), bound2.getX());
        double minY = Math.min(bound1.getY(), bound2.getY());
        double maxY = Math.max(bound1.getY(), bound2.getY());
        double minZ = Math.min(bound1.getZ(), bound2.getZ());
        double maxZ = Math.max(bound1.getZ(), bound2.getZ());
        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    /** The full "arena validate" checklist. Errors block enabling; warnings are just advice. */
    public List<ArenaCheck> report() {
        List<ArenaCheck> checks = new ArrayList<>();
        check(checks, lobby != null, Text.of("arena.check.lobby-ok"), Text.of("arena.check.lobby-fail"));
        check(checks, bound1 != null && bound2 != null, Text.of("arena.check.bounds-ok"), Text.of("arena.check.bounds-fail"));

        int loot = allLootPoints().size();
        if (loot < 3) {
            checks.add(new ArenaCheck(ArenaCheck.Level.ERROR, Text.of("arena.check.loot-fail", "count", loot)));
        } else if (loot < 6) {
            checks.add(new ArenaCheck(ArenaCheck.Level.WARN, Text.of("arena.check.loot-warn", "count", loot)));
        } else {
            checks.add(new ArenaCheck(ArenaCheck.Level.OK, Text.of("arena.check.loot-ok", "count", loot,
                    "common", getLootPoints().size(), "rare", getLootPoints(LootZone.RARE).size(),
                    "cache", getLootPoints(LootZone.CACHE).size())));
        }

        if (relicPoints.isEmpty()) {
            checks.add(new ArenaCheck(ArenaCheck.Level.WARN, Text.of("arena.check.relic-warn")));
        } else {
            checks.add(new ArenaCheck(ArenaCheck.Level.OK, Text.of("arena.check.relic-ok", "count", relicPoints.size())));
        }
        if (spectator == null) checks.add(new ArenaCheck(ArenaCheck.Level.WARN, Text.of("arena.check.spectator-warn")));

        for (Team team : Team.values()) {
            TeamSite site = sites.get(team);
            String name = team.displayName();
            check(checks, site.spawn != null, Text.of("arena.check.spawn-ok", "team", name),
                    Text.of("arena.check.spawn-fail", "team", name));
            check(checks, !site.dockChests.isEmpty(), Text.of("arena.check.dock-ok", "team", name, "count", site.dockChests.size()),
                    Text.of("arena.check.dock-fail", "team", name));
            check(checks, !site.vaultChests.isEmpty(), Text.of("arena.check.vault-ok", "team", name, "count", site.vaultChests.size()),
                    Text.of("arena.check.vault-fail", "team", name));
            check(checks, site.vaultDoor != null, Text.of("arena.check.vault-door-ok", "team", name),
                    Text.of("arena.check.vault-door-fail", "team", name));
            check(checks, site.golemIdle != null, Text.of("arena.check.golem-idle-ok", "team", name),
                    Text.of("arena.check.golem-idle-fail", "team", name));
            check(checks, !site.waypoints.isEmpty(), Text.of("arena.check.waypoints-ok", "team", name, "count", site.waypoints.size()),
                    Text.of("arena.check.waypoints-fail", "team", name));
            if (site.shop == null) checks.add(new ArenaCheck(ArenaCheck.Level.WARN, Text.of("arena.check.shop-warn", "team", name)));
            if (site.base() == null) checks.add(new ArenaCheck(ArenaCheck.Level.WARN, Text.of("arena.check.base-warn", "team", name)));
            if (site.vaultRegion() == null) {
                checks.add(new ArenaCheck(ArenaCheck.Level.WARN, Text.of("arena.check.vault-region-warn", "team", name)));
            }
        }
        return checks;
    }

    private static void check(List<ArenaCheck> checks, boolean ok, Text okText, Text failText) {
        checks.add(ok ? new ArenaCheck(ArenaCheck.Level.OK, okText) : new ArenaCheck(ArenaCheck.Level.ERROR, failText));
    }

    /** Only the blocking problems - empty means the arena can be enabled. */
    public List<ArenaCheck> validate() {
        List<ArenaCheck> errors = new ArrayList<>();
        for (ArenaCheck check : report()) {
            if (check.level() == ArenaCheck.Level.ERROR) errors.add(check);
        }
        return errors;
    }
}
