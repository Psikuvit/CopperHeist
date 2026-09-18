package me.psikuvit.copperHeist.shop.action;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Action id (the {@code action:} key in shop.yml) to implementation. Ships give-item, spawn-golem and command. */
public final class ShopActionRegistry {

    private final Map<String, ShopAction> actions = new HashMap<>();

    public ShopActionRegistry() {
        register("give-item", new GiveItemAction());
        register("spawn-golem", new SpawnGolemAction());
        register("command", new CommandAction());
    }

    public void register(String id, ShopAction action) {
        actions.put(id.toLowerCase(Locale.ROOT), action);
    }

    public ShopAction get(String id) {
        return actions.get(id.toLowerCase(Locale.ROOT));
    }
}
