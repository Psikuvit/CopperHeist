package me.psikuvit.copperHeist.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Layered configuration lookup. A path is resolved through each layer in order
 * (most specific first - later phases put arena overrides and presets in front
 * of the global config.yml) and finally falls back to the caller's coded default.
 * Layers are suppliers so a /ch reload is picked up without rebuilding anything.
 */
public final class Settings {

    private final List<Supplier<ConfigurationSection>> layers;

    private Settings(List<Supplier<ConfigurationSection>> layers) {
        this.layers = layers;
    }

    public static Settings of(Supplier<ConfigurationSection> base) {
        List<Supplier<ConfigurationSection>> layers = new ArrayList<>();
        layers.add(base);
        return new Settings(layers);
    }

    /** A new Settings that checks {@code layer} before everything this one already checks. */
    public Settings withOverride(Supplier<ConfigurationSection> layer) {
        List<Supplier<ConfigurationSection>> copy = new ArrayList<>();
        copy.add(layer);
        copy.addAll(layers);
        return new Settings(copy);
    }

    private ConfigurationSection find(String path) {
        for (Supplier<ConfigurationSection> layer : layers) {
            ConfigurationSection section = layer.get();
            if (section != null && section.isSet(path)) return section;
        }
        // Nothing sets it explicitly: the base layer's bundled defaults (or the caller's default) apply.
        return layers.getLast().get();
    }

    public boolean has(String path) {
        for (Supplier<ConfigurationSection> layer : layers) {
            ConfigurationSection section = layer.get();
            if (section != null && section.isSet(path)) return true;
        }
        return false;
    }

    public int getInt(String path, int def) {
        return find(path).getInt(path, def);
    }

    public long getLong(String path, long def) {
        return find(path).getLong(path, def);
    }

    public double getDouble(String path, double def) {
        return find(path).getDouble(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return find(path).getBoolean(path, def);
    }

    public String getString(String path, String def) {
        return find(path).getString(path, def);
    }

    public List<String> getStringList(String path) {
        return find(path).getStringList(path);
    }

    public List<Integer> getIntegerList(String path) {
        return find(path).getIntegerList(path);
    }
}
