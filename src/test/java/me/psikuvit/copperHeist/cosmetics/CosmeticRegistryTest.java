package me.psikuvit.copperHeist.cosmetics;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticRegistryTest {

    /** A stand-in effect that accepts trails and rejects a "bad" particle param, like a real one would. */
    private static final class FakeTrail implements EffectProvider {
        @Override
        public String id() {
            return "particle-trail";
        }

        @Override
        public Set<CosmeticCategory> categories() {
            return Set.of(CosmeticCategory.TRAIL, CosmeticCategory.KILL_EFFECT);
        }

        @Override
        public void validate(CosmeticDefinition cosmetic) {
            if ("NOPE".equals(cosmetic.params().get("particle"))) throw new IllegalArgumentException("unknown particle NOPE");
        }

        @Override
        public void play(EffectContext context) {
        }
    }

    private static EffectRegistry effects() {
        EffectRegistry registry = new EffectRegistry();
        registry.register(new FakeTrail());
        return registry;
    }

    private static ConfigurationSection section(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return config;
    }

    @Test
    void aCompleteEntryParses() throws Exception {
        CosmeticDefinition c = CosmeticRegistry.parse("Flame_Trail", section("""
                category: trail
                effect: particle-trail
                name: "<accent>Flame"
                description: ["<muted>Fire."]
                icon: BLAZE_POWDER
                rarity: epic
                price: 500
                level: 5
                params: { particle: FLAME }
                """), effects());
        assertEquals("flame_trail", c.id());
        assertEquals(CosmeticCategory.TRAIL, c.category());
        assertEquals(Rarity.EPIC, c.rarity());
        assertEquals(500, c.price());
        assertEquals(5, c.level());
        assertEquals("FLAME", c.params().get("particle"));
        assertEquals("copperheist.cosmetic.flame_trail", c.idPermission());
        assertTrue(c.purchasable());
    }

    @Test
    void aTitleNeedsNoEffect() throws Exception {
        CosmeticDefinition c = CosmeticRegistry.parse("thief", section("""
                category: title
                name: "<info>[Thief]"
                free: true
                level: 10
                """), effects());
        assertNull(c.effect());
        assertTrue(c.free());
        assertFalse(c.purchasable());
    }

    @Test
    void mistakesAreRejectedWithAReadableReason() {
        assertThrows(IllegalArgumentException.class, () -> CosmeticRegistry.parse("a", section("category: nonsense\neffect: particle-trail"), effects()));
        assertThrows(IllegalArgumentException.class, () -> CosmeticRegistry.parse("a", section("category: trail"), effects()));
        assertThrows(IllegalArgumentException.class, () -> CosmeticRegistry.parse("a", section("category: trail\neffect: missing"), effects()));
        assertThrows(IllegalArgumentException.class, () -> CosmeticRegistry.parse("a", section("category: victory\neffect: particle-trail"), effects()),
                "the effect must support the category");
        assertThrows(IllegalArgumentException.class, () -> CosmeticRegistry.parse("a",
                section("category: trail\neffect: particle-trail\nicon: NOT_A_MATERIAL"), effects()));
        assertThrows(IllegalArgumentException.class, () -> CosmeticRegistry.parse("a",
                section("category: trail\neffect: particle-trail\nparams: { particle: NOPE }"), effects()), "the effect validates its own params");
    }

    @Test
    void negativeNumbersAreClamped() throws Exception {
        CosmeticDefinition c = CosmeticRegistry.parse("a", section("category: trail\neffect: particle-trail\nprice: -5\nlevel: -2"), effects());
        assertEquals(0, c.price());
        assertEquals(0, c.level());
    }

    @Test
    void categoriesParseByTheirFileId() {
        assertEquals(CosmeticCategory.KILL_EFFECT, CosmeticCategory.parse("kill-effect"));
        assertEquals(CosmeticCategory.KILL_EFFECT, CosmeticCategory.parse("KILL_EFFECT"));
        assertNull(CosmeticCategory.parse("bogus"));
        assertEquals(Rarity.COMMON, Rarity.parse("nonsense"));
    }
}
