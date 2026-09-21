package me.psikuvit.copperHeist.npc;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.ui.Theme;
import me.psikuvit.copperHeist.util.LocationUtil;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Game navigators: NPCs standing in the hub that open the arena picker when right-clicked. They are placed with /ch navigator, saved in
 * navigators.yml, and built by the same NPC providers as shop keepers - so a navigator can be a villager with a profession, a Mannequin
 * with a skin or an armor stand with a custom head and armor (a navigator-looks.yml look). They are not saved into the world; the plugin spawns
 * them on start and re-creates any that go missing (a chunk that was unloaded, an entity that was removed).
 */
public class NavigatorService {

    /** One placed navigator; {@code look} is a navigator-looks.yml look id, or null for the default navigator look. */
    public record Navigator(String id, Location location, String look) {
    }

    private final CopperHeist plugin;
    private final File file;
    private final Map<String, Navigator> navigators = new LinkedHashMap<>();
    private final Map<String, NpcHandle> spawned = new HashMap<>();
    private final Map<UUID, String> byEntity = new HashMap<>();
    private BukkitTask task;

    public NavigatorService(CopperHeist plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "navigators.yml");
    }

    // ---- lifecycle ----

    public void start() {
        load();
        ensureSpawned();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::ensureSpawned, 100L, 100L);
    }

    public void stop() {
        if (task != null) task.cancel();
        despawnAll();
    }

    /** Re-reads navigators.yml and rebuilds every navigator (after /ch reload, so look changes show up). */
    public void reload() {
        despawnAll();
        load();
        ensureSpawned();
    }

    // ---- reading and writing ----

    private void load() {
        navigators.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("navigators");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) continue;
            Location loc = LocationUtil.deserialize(entry.getString("world"), entry.getList("loc"));
            if (loc == null) {
                plugin.getLogger().warning("Navigator '" + id + "' is in a world that isn't loaded - skipped.");
                continue;
            }
            navigators.put(id.toLowerCase(Locale.ROOT), new Navigator(id.toLowerCase(Locale.ROOT), loc, entry.getString("look")));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Navigator navigator : navigators.values()) {
            String base = "navigators." + navigator.id();
            yaml.set(base + ".world", navigator.location().getWorld().getName());
            yaml.set(base + ".loc", LocationUtil.serialize(navigator.location()));
            if (navigator.look() != null) yaml.set(base + ".look", navigator.look());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save navigators.yml", ex);
        }
    }

    // ---- managing ----

    public Collection<Navigator> all() {
        return new ArrayList<>(navigators.values());
    }

    public Navigator get(String id) {
        return id == null ? null : navigators.get(id.toLowerCase(Locale.ROOT));
    }

    /** Places (or moves) a navigator. */
    public void create(String id, Location location, String look) {
        String key = id.toLowerCase(Locale.ROOT);
        despawn(key);
        navigators.put(key, new Navigator(key, LocationUtil.center(location), look == null ? null : look.toLowerCase(Locale.ROOT)));
        save();
        ensureSpawned();
    }

    public boolean remove(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        if (navigators.remove(key) == null) return false;
        despawn(key);
        save();
        return true;
    }

    public boolean setLook(String id, String look) {
        Navigator navigator = get(id);
        if (navigator == null) return false;
        despawn(navigator.id());
        navigators.put(navigator.id(), new Navigator(navigator.id(), navigator.location(), look == null ? null : look.toLowerCase(Locale.ROOT)));
        save();
        ensureSpawned();
        return true;
    }

    /** The id of the navigator whose entity this is, or null. */
    public String idOf(UUID entityId) {
        return byEntity.get(entityId);
    }

    // ---- spawning ----

    /** Spawns every navigator that has no living entity and whose chunk is loaded. */
    private void ensureSpawned() {
        for (Navigator navigator : navigators.values()) {
            NpcHandle handle = spawned.get(navigator.id());
            if (handle != null && handle.clickable().isValid()) continue;
            World world = navigator.location().getWorld();
            if (world == null || !world.isChunkLoaded(navigator.location().getBlockX() >> 4, navigator.location().getBlockZ() >> 4)) continue;
            despawn(navigator.id());
            spawn(navigator);
        }
    }

    private void spawn(Navigator navigator) {
        NpcLook look = plugin.getNavigatorLooks().choose(navigator.look());
        // A navigator's own type setting (navigator.type), not the shop keepers' npc.type.
        String type = look != null && look.type() != null ? look.type() : plugin.settings().getString("navigator.type", "villager");
        Component name = plugin.getMessageService().get("npc.navigator-name");
        if (look != null && look.name() != null) name = Theme.mini().deserialize(look.name());

        NpcSpec spec = new NpcSpec(navigator.location(), name, null, plugin.settings(), look);
        NpcHandle handle = NpcSpawner.spawn(plugin, type, spec, "navigator '" + navigator.id() + "'");
        if (handle == null) return;
        spawned.put(navigator.id(), handle);
        for (Entity entity : NpcSpawner.entities(handle)) {
            entity.setPersistent(false); // spawned fresh on every start; never saved into the world
            Pdc.set(entity, PdcKeys.NAVIGATOR, navigator.id());
        }
        byEntity.put(handle.clickable().getUniqueId(), navigator.id());
    }

    private void despawn(String id) {
        NpcHandle handle = spawned.remove(id);
        if (handle == null) return;
        byEntity.remove(handle.clickable().getUniqueId());
        for (Entity entity : NpcSpawner.entities(handle)) entity.remove();
    }

    private void despawnAll() {
        for (String id : new ArrayList<>(spawned.keySet())) despawn(id);
        byEntity.clear();
    }
}
