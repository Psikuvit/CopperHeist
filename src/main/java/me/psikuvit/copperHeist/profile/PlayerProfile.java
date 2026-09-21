package me.psikuvit.copperHeist.profile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything about a player that is not a counter: when they first joined, flags like "tutorial done", what they own and what they
 * have equipped. Counters (XP, coins, wins ...) are stats and live in the stats system. Only touch a profile from the main thread;
 * the profile service copies it ({@link #snapshot()}) before handing it to the database thread.
 */
public final class PlayerProfile {

    /** An immutable copy of a profile, safe to hand to another thread. */
    public record Data(Map<String, String> fields, Map<String, Long> unlocks, Map<String, String> equipped) {

        public static Data empty() {
            return new Data(Map.of(), Map.of(), Map.of());
        }
    }

    public static final String FIRST_JOIN = "first_join";
    public static final String LAST_LOGIN = "last_login";

    private final UUID uuid;
    private final Map<String, String> fields = new LinkedHashMap<>();
    private final Map<String, Long> unlocks = new LinkedHashMap<>();
    private final Map<String, String> equipped = new LinkedHashMap<>();
    private final boolean newPlayer;
    private boolean dirty;

    public PlayerProfile(UUID uuid, Data data, boolean newPlayer) {
        this.uuid = uuid;
        this.fields.putAll(data.fields());
        this.unlocks.putAll(data.unlocks());
        this.equipped.putAll(data.equipped());
        this.newPlayer = newPlayer;
    }

    public UUID uuid() {
        return uuid;
    }

    /** True for the session in which this player's profile was first created - the hook for welcome rewards and the tutorial. */
    public boolean isNewPlayer() {
        return newPlayer;
    }

    // ---- scalar fields ----

    public String field(String name) {
        return fields.get(name);
    }

    public long longField(String name, long fallback) {
        String value = fields.get(name);
        if (value == null) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public void setField(String name, String value) {
        if (value == null) {
            if (fields.remove(name) != null) dirty = true;
        } else if (!value.equals(fields.put(name, value))) {
            dirty = true;
        }
    }

    public void setField(String name, long value) {
        setField(name, String.valueOf(value));
    }

    /** Removes every field whose name starts with the prefix (a whole group, e.g. one period's quest progress). */
    public void removeFields(String prefix) {
        if (fields.keySet().removeIf(name -> name.startsWith(prefix))) dirty = true;
    }

    public boolean flag(String name) {
        return "1".equals(fields.get("flag." + name));
    }

    public void setFlag(String name, boolean on) {
        setField("flag." + name, on ? "1" : null);
    }

    // ---- what the player owns ----

    public boolean owns(String id) {
        return unlocks.containsKey(id);
    }

    public Set<String> unlocked() {
        return Set.copyOf(unlocks.keySet());
    }

    /** Adds an item to what the player owns. Returns false if they already had it. */
    public boolean unlock(String id) {
        if (unlocks.containsKey(id)) return false;
        unlocks.put(id, System.currentTimeMillis());
        dirty = true;
        return true;
    }

    /** Takes an item away (and unequips it wherever it was equipped). Returns false if they didn't have it. */
    public boolean revoke(String id) {
        if (unlocks.remove(id) == null) return false;
        if (equipped.values().removeIf(id::equals)) dirty = true;
        dirty = true;
        return true;
    }

    // ---- what the player has equipped ----

    public String equipped(String category) {
        return equipped.get(category);
    }

    public Map<String, String> allEquipped() {
        return Map.copyOf(equipped);
    }

    /** Equips an item they own in a category; returns false (and changes nothing) if they don't own it. */
    public boolean equip(String category, String id) {
        if (!unlocks.containsKey(id)) return false;
        if (!id.equals(equipped.put(category, id))) dirty = true;
        return true;
    }

    public void unequip(String category) {
        if (equipped.remove(category) != null) dirty = true;
    }

    // ---- saving ----

    public boolean isDirty() {
        return dirty;
    }

    /** Copies the current state for saving and clears the dirty flag (put it back with {@link #markDirty()} if the save fails). */
    public Data snapshot() {
        dirty = false;
        return new Data(new LinkedHashMap<>(fields), new LinkedHashMap<>(unlocks), new LinkedHashMap<>(equipped));
    }

    public void markDirty() {
        dirty = true;
    }
}
