package me.psikuvit.copperHeist.role;

import java.util.Map;

/** A role's active ability: which registered ability to run plus its free-form YAML parameters. */
public record AbilitySpec(String id, Map<String, Object> params) {

    public double number(String key, double def) {
        return params.get(key) instanceof Number n ? n.doubleValue() : def;
    }

    public String text(String key, String def) {
        return params.get(key) instanceof String s ? s : def;
    }

    public long cooldownSeconds() {
        return (long) number("cooldown-seconds", 30);
    }
}
