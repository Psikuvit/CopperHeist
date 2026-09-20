package me.psikuvit.copperHeist.cosmetics;

import me.psikuvit.copperHeist.menu.PagedMenu;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticMenusTest {

    private static CosmeticDefinition cosmetic(String id) {
        return new CosmeticDefinition(id, CosmeticCategory.TRAIL, "x", id, List.of(), Material.PAPER, Rarity.COMMON, 100, 0, null, false, false, Map.of());
    }

    private static YamlConfiguration lang() throws Exception {
        try (InputStream in = CosmeticMenusTest.class.getClassLoader().getResourceAsStream("lang/en.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void pagesAreCountedAndClamped() {
        assertEquals(1, PagedMenu.pageCount(0), "an empty list still has one page");
        assertEquals(1, PagedMenu.pageCount(28));
        assertEquals(2, PagedMenu.pageCount(29));
        assertEquals(0, PagedMenu.clamp(-3, 4));
        assertEquals(3, PagedMenu.clamp(9, 4));
        assertEquals(0, PagedMenu.clamp(5, 1), "the list shrank to one page while the menu was open");
    }

    @Test
    void theFeaturedPickIsTheSameForEveryoneOnADayAndChangesBetweenDays() {
        List<CosmeticDefinition> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) all.add(cosmetic("item" + i));

        List<CosmeticDefinition> monday = CosmeticService.pickFeatured(all, 3, 20_000);
        assertEquals(3, monday.size());
        assertEquals(monday, CosmeticService.pickFeatured(all, 3, 20_000));

        List<CosmeticDefinition> reversed = new ArrayList<>(all);
        Collections.reverse(reversed);
        assertEquals(monday, CosmeticService.pickFeatured(reversed, 3, 20_000), "the order the list arrives in must not matter");

        boolean changes = false;
        for (long day = 20_001; day < 20_010; day++) changes |= !monday.equals(CosmeticService.pickFeatured(all, 3, day));
        assertTrue(changes, "the picks rotate");
    }

    @Test
    void featuredHandlesSmallOrEmptyLists() {
        assertTrue(CosmeticService.pickFeatured(List.of(), 3, 1).isEmpty());
        assertEquals(1, CosmeticService.pickFeatured(List.of(cosmetic("only")), 3, 1).size());
        assertTrue(CosmeticService.pickFeatured(List.of(cosmetic("a")), 0, 1).isEmpty());
        assertNotEquals(null, CosmeticService.pickFeatured(List.of(cosmetic("a")), -2, 1));
    }

    @Test
    void everyOutcomeRarityAndCategoryHasAMessage() throws Exception {
        YamlConfiguration lang = lang();
        for (CosmeticService.Outcome outcome : CosmeticService.Outcome.values()) {
            assertTrue(lang.isString("cosmetics.outcome." + outcome.name().toLowerCase()), "outcome " + outcome);
        }
        for (Rarity rarity : Rarity.values()) assertTrue(lang.isString("cosmetics.rarity." + rarity.key()), "rarity " + rarity);
        for (CosmeticCategory category : CosmeticCategory.values()) assertTrue(lang.isString(category.langKey()), "category " + category);
    }
}
