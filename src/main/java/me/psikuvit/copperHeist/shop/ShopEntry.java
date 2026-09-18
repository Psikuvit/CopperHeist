package me.psikuvit.copperHeist.shop;

import me.psikuvit.copperHeist.game.GameState;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * One row of shop.yml. {@code action} names the {@link me.psikuvit.copperHeist.shop.action.ShopAction}
 * that runs when it's bought; {@code params} is the raw YAML section so actions can read their own keys.
 * {@code minPhase} (null = any time), {@code maxPerPlayer} and {@code cooldownSeconds} of 0 mean unlimited.
 */
public record ShopEntry(String id, String action, Material material, int amount, int cost, String name,
                        List<String> lore, GameState minPhase, int maxPerPlayer, int cooldownSeconds, int slot,
                        Map<String, Object> params) {

    public String text(String key, String def) {
        return params.get(key) instanceof String s ? s : def;
    }

    public boolean flag(String key) {
        return params.get(key) instanceof Boolean b && b;
    }
}
