package me.psikuvit.copperHeist.cosmetics;

import me.psikuvit.copperHeist.cosmetics.CosmeticRules.Purchase;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticRulesTest {

    private static CosmeticDefinition cosmetic(long price, int level, boolean free, boolean hidden) {
        return new CosmeticDefinition("flame", CosmeticCategory.TRAIL, "particle-trail", "Flame", List.of(), Material.BLAZE_POWDER,
                Rarity.RARE, price, level, null, free, hidden, Map.of());
    }

    @Test
    void ownedOrPermittedAlwaysHasAccess() {
        CosmeticDefinition c = cosmetic(500, 10, false, false);
        assertTrue(CosmeticRules.hasAccess(c, true, false, 1));
        assertTrue(CosmeticRules.hasAccess(c, false, true, 1));
        assertFalse(CosmeticRules.hasAccess(c, false, false, 50));
    }

    @Test
    void aFreeCosmeticUnlocksAtItsLevel() {
        CosmeticDefinition c = cosmetic(0, 10, true, false);
        assertFalse(CosmeticRules.hasAccess(c, false, false, 9));
        assertTrue(CosmeticRules.hasAccess(c, false, false, 10));
    }

    @Test
    void buyingChecksLevelBeforePrice() {
        CosmeticDefinition c = cosmetic(500, 5, false, false);
        assertEquals(Purchase.LEVEL_TOO_LOW, CosmeticRules.canBuy(c, false, 4, 0), "the first thing in the way is reported");
        assertEquals(Purchase.NOT_ENOUGH_COINS, CosmeticRules.canBuy(c, false, 5, 499));
        assertEquals(Purchase.OK, CosmeticRules.canBuy(c, false, 5, 500));
    }

    @Test
    void thingsYouAlreadyHaveOrCannotBuyAreRefused() {
        assertEquals(Purchase.ALREADY_HAVE, CosmeticRules.canBuy(cosmetic(500, 0, false, false), true, 99, 9999));
        assertEquals(Purchase.NOT_FOR_SALE, CosmeticRules.canBuy(cosmetic(0, 0, false, false), false, 99, 9999));
    }

    @Test
    void hiddenCosmeticsShowOnlyOnceYouHaveThem() {
        CosmeticDefinition hidden = cosmetic(500, 0, false, true);
        assertFalse(CosmeticRules.visible(hidden, false));
        assertTrue(CosmeticRules.visible(hidden, true));
        assertTrue(CosmeticRules.visible(cosmetic(500, 0, false, false), false));
    }
}
