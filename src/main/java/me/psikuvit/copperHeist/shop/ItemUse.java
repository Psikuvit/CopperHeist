package me.psikuvit.copperHeist.shop;

/**
 * Tag ids that mark an item as usable in some special way (right-click a golem, throw at golems,
 * place on the ground...). A shop entry gets one by setting {@code tag: true}; the id is the entry id.
 * The listeners that implement the behavior look for these exact strings.
 */
public final class ItemUse {

    public static final String HONEYCOMB = "honeycomb";
    public static final String STORM_ROD = "storm_rod";
    public static final String OXIDIZER_SPLASH = "oxidizer_splash";
    public static final String ALARM = "alarm";
    public static final String VAULT_DRILL = "vault_drill";

    private ItemUse() {
    }
}
