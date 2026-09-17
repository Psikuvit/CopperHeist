package me.psikuvit.copperHeist.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Cooldowns {

    private final Map<UUID, Long> expiries = new HashMap<>();

    public boolean isReady(UUID id) {
        Long expiry = expiries.get(id);
        return expiry == null || System.currentTimeMillis() >= expiry;
    }

    public long remainingSeconds(UUID id) {
        Long expiry = expiries.get(id);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    public void set(UUID id, long durationSeconds) {
        expiries.put(id, System.currentTimeMillis() + durationSeconds * 1000);
    }

    public void clear(UUID id) {
        expiries.remove(id);
    }
}
