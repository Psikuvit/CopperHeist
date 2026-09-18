package me.psikuvit.copperHeist.arena;

import me.psikuvit.copperHeist.game.Team;
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
    }

    private final String name;
    private String worldName;
    private boolean enabled = false;
    private Location lobby;
    private Location bound1;
    private Location bound2;
    private final Map<Team, TeamSite> sites = new EnumMap<>(Team.class);
    private final Map<LootZone, List<Location>> lootZones = new EnumMap<>(LootZone.class);
    private final List<Location> relicPoints = new ArrayList<>();

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

    public List<String> validate() {
        List<String> issues = new ArrayList<>();
        if (lobby == null) issues.add("No lobby set");
        if (bound1 == null || bound2 == null) issues.add("Arena bounds not set (setbounds1/setbounds2)");
        int lootTotal = allLootPoints().size();
        if (lootTotal < 3) issues.add("Only " + lootTotal + " loot points (recommend 3+)");
        for (Team team : Team.values()) {
            TeamSite site = sites.get(team);
            String label = team.displayName();
            if (site.spawn == null) issues.add(label + " team has no spawn");
            if (site.dockChests.isEmpty()) issues.add(label + " team has no dock chests");
            if (site.vaultChests.isEmpty()) issues.add(label + " team has no vault chests");
            if (site.golemIdle == null) issues.add(label + " team has no golem idle point");
            if (site.waypoints.isEmpty()) issues.add(label + " team has no waypoints");
            if (site.vaultDoor == null) issues.add(label + " team has no vault door (Vault Drill breaches won't work)");
        }
        return issues;
    }
}
