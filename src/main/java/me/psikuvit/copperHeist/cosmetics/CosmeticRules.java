package me.psikuvit.copperHeist.cosmetics;

/**
 * The rules for who may have a cosmetic, with no server types so they can be tested. The service supplies the facts (level, coins,
 * whether the player owns it or holds its permission) and gets one clear answer back.
 */
public final class CosmeticRules {

    /** What happens if a player tries to buy a cosmetic. */
    public enum Purchase {
        OK,
        ALREADY_HAVE,
        NOT_FOR_SALE,
        LEVEL_TOO_LOW,
        NOT_ENOUGH_COINS
    }

    private CosmeticRules() {
    }

    /**
     * Owning it or holding its permission always counts. A free cosmetic counts once the player has reached its level, so a free
     * "level 10" title unlocks by itself at level 10 without anyone having to buy it.
     */
    public static boolean hasAccess(CosmeticDefinition cosmetic, boolean owned, boolean hasPermission, int playerLevel) {
        return owned || hasPermission || (cosmetic.free() && playerLevel >= cosmetic.level());
    }

    /** Level is checked before price so the message tells players the first thing standing in their way. */
    public static Purchase canBuy(CosmeticDefinition cosmetic, boolean hasAccess, int playerLevel, long coins) {
        if (hasAccess) return Purchase.ALREADY_HAVE;
        if (!cosmetic.purchasable()) return Purchase.NOT_FOR_SALE;
        if (playerLevel < cosmetic.level()) return Purchase.LEVEL_TOO_LOW;
        if (coins < cosmetic.price()) return Purchase.NOT_ENOUGH_COINS;
        return Purchase.OK;
    }

    /** Whether a cosmetic should appear in a menu for a player: hidden ones only once they have them. */
    public static boolean visible(CosmeticDefinition cosmetic, boolean hasAccess) {
        return !cosmetic.hidden() || hasAccess;
    }
}
