package me.psikuvit.copperHeist.npc;

import java.util.Map;

/**
 * One named appearance for an NPC, from npcs.yml: which kind of NPC it is (villager, mannequin, armor-stand ...), an optional name
 * and the options that kind understands. Options are flat and dotted (for example {@code skin.value}, {@code armor.chest}).
 *
 * @param id      the look's id in npcs.yml
 * @param type    the NPC provider to use, or null to use {@code npc.type} from config.yml
 * @param name    MiniMessage name tag, or null for the default ({@code {team}} is replaced by the team's name)
 * @param options everything else, read by the provider (see {@code npcs.yml} for what each provider supports)
 */
public record NpcLook(String id, String type, String name, Map<String, Object> options) {

    public String string(String key) {
        Object value = options.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public boolean has(String key) {
        return options.containsKey(key);
    }

    public boolean bool(String key, boolean fallback) {
        Object value = options.get(key);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    public int integer(String key, int fallback) {
        Object value = options.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
