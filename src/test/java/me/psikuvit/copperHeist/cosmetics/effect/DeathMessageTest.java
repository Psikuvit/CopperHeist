package me.psikuvit.copperHeist.cosmetics.effect;

import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.Rarity;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathMessageTest {

    private static CosmeticDefinition with(Map<String, Object> params) {
        return new CosmeticDefinition("m", CosmeticCategory.DEATH_MESSAGE, "death-message", "m", List.of(), Material.SKELETON_SKULL, Rarity.COMMON,
                0, 0, null, true, false, params);
    }

    @Test
    void aMessageNeedsTheVictim() {
        DeathMessageEffect effect = new DeathMessageEffect();
        assertDoesNotThrow(() -> effect.validate(with(Map.of("message", "<accent>{killer}</accent> got {victim}"))));
        assertDoesNotThrow(() -> effect.validate(with(Map.of("message", "{victim} was outplayed"))), "the killer's name is optional");
        assertThrows(IllegalArgumentException.class, () -> effect.validate(with(Map.of("message", "{killer} wins"))));
        assertThrows(IllegalArgumentException.class, () -> effect.validate(with(Map.of("message", " "))));
        assertThrows(IllegalArgumentException.class, () -> effect.validate(with(Map.of())));
    }

    @Test
    void namesAreFilledInEverywhere() {
        assertEquals("<b>Ann</b> beat Bob, Bob!", DeathMessageEffect.fill("<b>{killer}</b> beat {victim}, {victim}!", "Ann", "Bob"));
    }

    @Test
    void onlyTheDeathMessageCategoryIsAccepted() {
        assertEquals(Set.of(CosmeticCategory.DEATH_MESSAGE), new DeathMessageEffect().categories());
        assertTrue(CosmeticCategory.parse("death-message") == CosmeticCategory.DEATH_MESSAGE);
        assertEquals("cosmetics.category.death-message", CosmeticCategory.DEATH_MESSAGE.langKey());
    }
}
