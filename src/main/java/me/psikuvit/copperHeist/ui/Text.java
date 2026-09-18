package me.psikuvit.copperHeist.ui;

/** A translatable message reference: a lang key plus its placeholder pairs ({name, value, name, value...}). */
public record Text(String key, Object... args) {

    public static Text of(String key, Object... args) {
        return new Text(key, args);
    }
}
