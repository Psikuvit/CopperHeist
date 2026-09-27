package me.psikuvit.copperHeist.npc;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.npc.NavigatorRegistry.Navigator;
import me.psikuvit.copperHeist.ui.Theme;
import me.psikuvit.copperHeist.util.LocationUtil;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Game navigators: NPCs standing in the hub that open the arena picker when right-clicked. They are placed with /ch navigator, saved in
 * navigators.yml ({@link NavigatorRegistry}), and built by the same NPC providers as shop keepers - so a navigator can be a villager with a
 * profession, a Mannequin with a skin or an armor stand with a custom head and armor (a navigator-looks.yml look). They are not saved into the
 * world; the plugin spawns them on start and re-creates any that go missing (a chunk that was unloaded, an entity that was removed).
 */
public class NavigatorService {

    private final CopperHeist plugin;
    private final NavigatorRegistry registry;
    private final Map<String, NpcHandle> spawned = new HashMap<>();
    private final Map<UUID, String> byEntity = new HashMap<>();
    private BukkitTask task;

    public NavigatorService(CopperHeist plugin, NavigatorRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public NavigatorRegistry registry() {
        return registry;
    }

    // ---- lifecycle ----

    public void start() {
        registry.load();
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
        registry.load();
        ensureSpawned();
    }

    // ---- managing ----

    /** Places (or moves) a navigator. */
    public void create(String id, Location location, String look) {
        despawn(registry.put(id, LocationUtil.center(location), look).id());
        ensureSpawned();
    }

    public boolean remove(String id) {
        Navigator removed = registry.remove(id);
        if (removed == null) return false;
        despawn(removed.id());
        return true;
    }

    public boolean setLook(String id, String look) {
        Navigator navigator = registry.get(id);
        if (navigator == null) return false;
        despawn(navigator.id());
        registry.put(navigator.id(), navigator.location(), look);
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
        for (Navigator navigator : registry.all()) {
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
