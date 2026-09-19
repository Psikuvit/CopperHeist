package me.psikuvit.copperHeist.util;

import me.psikuvit.copperHeist.loot.LootItem;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Bookkeeping for every entity the plugin puts into the world (golems, labels, NPCs, hitboxes, displays).
 * Each one is tagged with the id of the current server session. Normal cleanup removes them when a match ends;
 * this class is the safety net for everything else - entities left behind by a crash, by a chunk that was
 * unloaded at the wrong moment, or by an older version. Anything tagged with a different session is stale and gets removed.
 */
public final class HeistEntities {

    private static String session = "";

    private HeistEntities() {
    }

    public static void init(String sessionId) {
        session = sessionId;
    }

    /** Tags a freshly spawned match entity and keeps it across chunk unloads (the sweeps below remove it later). */
    public static void mark(Entity entity) {
        entity.setPersistent(true);
        Pdc.set(entity, PdcKeys.SESSION, session);
    }

    /** True for an entity left over from another session (or from before sessions existed). */
    public static boolean isStale(Entity entity) {
        String tagged = Pdc.get(entity, PdcKeys.SESSION);
        if (tagged != null) return !tagged.equals(session);
        return Pdc.has(entity, PdcKeys.MATCH_ID);
    }

    /** True for loot lying on the ground that belongs to a match that isn't running (e.g. from before a restart). */
    public static boolean isStaleLoot(Entity entity, Predicate<String> matchRunning) {
        if (!(entity instanceof Item item) || !LootItem.isLoot(item.getItemStack())) return false;
        String matchId = LootItem.getMatchId(item.getItemStack());
        return matchId == null || !matchRunning.test(matchId);
    }

    /** Removes stale entities from the given ones; returns how many. */
    public static int sweepStale(Collection<? extends Entity> entities, Predicate<String> matchRunning) {
        int removed = 0;
        for (Entity entity : entities) {
            if (isStale(entity) || isStaleLoot(entity, matchRunning)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    public static int sweepStaleEverywhere(Predicate<String> matchRunning) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) removed += sweepStale(world.getEntities(), matchRunning);
        return removed;
    }

    /** Removes every entity the plugin tagged (this session's or an old one's) plus stray match loot; returns how many. */
    public static int removeAllTagged() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (Pdc.has(entity, PdcKeys.SESSION) || Pdc.has(entity, PdcKeys.MATCH_ID) || isStaleLoot(entity, id -> false)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    /** Shutdown: removes everything this session spawned that is still around, in every loaded world. */
    public static int removeCurrentSession() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (session.equals(Pdc.get(entity, PdcKeys.SESSION))) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }
}
