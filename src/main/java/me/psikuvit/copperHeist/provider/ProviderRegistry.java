package me.psikuvit.copperHeist.provider;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Id-to-factory lookup for one selectable feature (NPC type, menu style, respawn mode...). The id comes
 * from config; an unknown id logs one warning and falls back to the default, so a typo in config.yml can
 * never take a feature down. Third-party plugins add their own choices with {@link #register}.
 */
public final class ProviderRegistry<T> {

    private final String featureName;
    private final String defaultId;
    private final Logger logger;
    private final Map<String, Supplier<T>> factories = new LinkedHashMap<>();
    private final Set<String> warned = new HashSet<>();

    public ProviderRegistry(String featureName, String defaultId, Logger logger) {
        this.featureName = featureName;
        this.defaultId = defaultId.toLowerCase(Locale.ROOT);
        this.logger = logger;
    }

    public void register(String id, Supplier<T> factory) {
        factories.put(id.toLowerCase(Locale.ROOT), factory);
    }

    public Set<String> ids() {
        return factories.keySet();
    }

    public boolean has(String id) {
        return id != null && factories.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public T resolve(String id) {
        String key = id == null ? defaultId : id.toLowerCase(Locale.ROOT);
        Supplier<T> factory = factories.get(key);
        if (factory == null) {
            if (warned.add(key)) {
                logger.warning("Unknown " + featureName + " '" + id + "' in config - falling back to '" + defaultId
                        + "'. Available: " + String.join(", ", factories.keySet()));
            }
            factory = factories.get(defaultId);
        }
        return factory.get();
    }
}
