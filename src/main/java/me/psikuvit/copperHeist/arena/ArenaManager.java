package me.psikuvit.copperHeist.arena;

import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.util.LocationUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import org.bukkit.Location;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ArenaManager {

    private final Plugin plugin;
    private final File arenasFolder;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(Plugin plugin) {
        this.plugin = plugin;
        this.arenasFolder = new File(plugin.getDataFolder(), "arenas");
        if (!arenasFolder.exists()) arenasFolder.mkdirs();
    }

    public void loadAll() {
        arenas.clear();
        File[] files = arenasFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            try {
                Arena arena = load(file);
                arenas.put(arena.getName().toLowerCase(), arena);
                plugin.getLogger().info("Loaded arena '" + arena.getName() + "'");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load arena from " + file.getName(), ex);
            }
        }
    }

    /** Where "arena snapshot" stores the arena's saved block layout for the snapshot reset method. */
    public File snapshotFile(Arena arena) {
        return new File(arenasFolder, arena.getName().toLowerCase() + ".snapshot");
    }

    public Arena create(String name) {
        Arena arena = new Arena(name);
        arenas.put(name.toLowerCase(), arena);
        return arena;
    }

    public Arena get(String name) {
        if (name == null) return null;
        return arenas.get(name.toLowerCase());
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    public void save(Arena arena) {
        File file = new File(arenasFolder, arena.getName().toLowerCase() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", arena.getName());
        yaml.set("enabled", arena.isEnabled());
        yaml.set("world", arena.getWorldName());
        if (arena.getLobby() != null) yaml.set("lobby", LocationUtil.serialize(arena.getLobby()));
        if (arena.getSpectator() != null) yaml.set("spectator", LocationUtil.serialize(arena.getSpectator()));
        if (arena.getBound1() != null) yaml.set("bound1", LocationUtil.serialize(arena.getBound1()));
        if (arena.getBound2() != null) yaml.set("bound2", LocationUtil.serialize(arena.getBound2()));
        yaml.set("loot-points", LocationUtil.serializeList(arena.getLootPoints()));
        yaml.set("rare-points", LocationUtil.serializeList(arena.getLootPoints(Arena.LootZone.RARE)));
        yaml.set("cache-points", LocationUtil.serializeList(arena.getLootPoints(Arena.LootZone.CACHE)));
        yaml.set("relic-points", LocationUtil.serializeList(arena.getRelicPoints()));
        List<Map<String, Object>> pads = new ArrayList<>();
        for (Arena.GustPad pad : arena.getGustPads()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("loc", LocationUtil.serialize(pad.location()));
            entry.put("power", pad.power());
            pads.add(entry);
        }
        yaml.set("gust-pads", pads);
        for (Team team : Team.values()) {
            Arena.TeamSite site = arena.site(team);
            String base = "teams." + team.name().toLowerCase();
            if (site.spawn != null) yaml.set(base + ".spawn", LocationUtil.serialize(site.spawn));
            if (site.golemIdle != null) yaml.set(base + ".golem-idle", LocationUtil.serialize(site.golemIdle));
            if (site.vaultDoor != null) yaml.set(base + ".vault-door", LocationUtil.serialize(site.vaultDoor));
            if (site.shop != null) yaml.set(base + ".shop", LocationUtil.serialize(site.shop));
            if (site.baseCorner1 != null) yaml.set(base + ".base-corner-1", LocationUtil.serialize(site.baseCorner1));
            if (site.baseCorner2 != null) yaml.set(base + ".base-corner-2", LocationUtil.serialize(site.baseCorner2));
            if (site.vaultCorner1 != null) yaml.set(base + ".vault-corner-1", LocationUtil.serialize(site.vaultCorner1));
            if (site.vaultCorner2 != null) yaml.set(base + ".vault-corner-2", LocationUtil.serialize(site.vaultCorner2));
            yaml.set(base + ".dock-chests", LocationUtil.serializeList(site.dockChests));
            yaml.set(base + ".vault-chests", LocationUtil.serializeList(site.vaultChests));
            yaml.set(base + ".waypoints", LocationUtil.serializeList(site.waypoints));
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save arena " + arena.getName(), ex);
        }
    }

    private Arena load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String name = yaml.getString("name", file.getName().replace(".yml", ""));
        Arena arena = new Arena(name);
        String world = yaml.getString("world");
        arena.setWorldName(world);
        arena.setEnabled(yaml.getBoolean("enabled", false));
        if (yaml.contains("lobby")) arena.setLobby(LocationUtil.deserialize(world, yaml.getList("lobby")));
        if (yaml.contains("spectator")) arena.setSpectator(LocationUtil.deserialize(world, yaml.getList("spectator")));
        if (yaml.contains("bound1")) arena.setBound1(LocationUtil.deserialize(world, yaml.getList("bound1")));
        if (yaml.contains("bound2")) arena.setBound2(LocationUtil.deserialize(world, yaml.getList("bound2")));
        arena.getLootPoints().addAll(LocationUtil.deserializeList(world, yaml.getList("loot-points")));
        arena.getLootPoints(Arena.LootZone.RARE).addAll(LocationUtil.deserializeList(world, yaml.getList("rare-points")));
        arena.getLootPoints(Arena.LootZone.CACHE).addAll(LocationUtil.deserializeList(world, yaml.getList("cache-points")));
        arena.getRelicPoints().addAll(LocationUtil.deserializeList(world, yaml.getList("relic-points")));
        for (Map<?, ?> entry : yaml.getMapList("gust-pads")) {
            Location loc = LocationUtil.deserialize(world, entry.get("loc") instanceof List<?> l ? l : null);
            double power = entry.get("power") instanceof Number n ? n.doubleValue() : 1.4;
            if (loc != null) arena.getGustPads().add(new Arena.GustPad(loc, power));
        }
        for (Team team : Team.values()) {
            Arena.TeamSite site = arena.site(team);
            String base = "teams." + team.name().toLowerCase();
            if (yaml.contains(base + ".spawn")) site.spawn = LocationUtil.deserialize(world, yaml.getList(base + ".spawn"));
            if (yaml.contains(base + ".golem-idle")) site.golemIdle = LocationUtil.deserialize(world, yaml.getList(base + ".golem-idle"));
            if (yaml.contains(base + ".shop")) site.shop = LocationUtil.deserialize(world, yaml.getList(base + ".shop"));
            if (yaml.contains(base + ".base-corner-1")) site.baseCorner1 = LocationUtil.deserialize(world, yaml.getList(base + ".base-corner-1"));
            if (yaml.contains(base + ".base-corner-2")) site.baseCorner2 = LocationUtil.deserialize(world, yaml.getList(base + ".base-corner-2"));
            if (yaml.contains(base + ".vault-corner-1")) site.vaultCorner1 = LocationUtil.deserialize(world, yaml.getList(base + ".vault-corner-1"));
            if (yaml.contains(base + ".vault-corner-2")) site.vaultCorner2 = LocationUtil.deserialize(world, yaml.getList(base + ".vault-corner-2"));
            if (yaml.contains(base + ".vault-door")) site.vaultDoor = LocationUtil.deserialize(world, yaml.getList(base + ".vault-door"));
            site.dockChests.addAll(LocationUtil.deserializeList(world, yaml.getList(base + ".dock-chests")));
            site.vaultChests.addAll(LocationUtil.deserializeList(world, yaml.getList(base + ".vault-chests")));
            site.waypoints.addAll(LocationUtil.deserializeList(world, yaml.getList(base + ".waypoints")));
        }
        return arena;
    }
}
