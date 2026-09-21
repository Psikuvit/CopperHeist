package me.psikuvit.copperHeist.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaAdvancementTest {

    @Test
    void vanillaAdvancementsAreRemovedButRecipesAndOtherPluginsAreKept() {
        assertTrue(VanillaAdvancementListener.isVanillaAdvancement("minecraft", "story/root"));
        assertTrue(VanillaAdvancementListener.isVanillaAdvancement("minecraft", "adventure/kill_a_mob"));
        assertFalse(VanillaAdvancementListener.isVanillaAdvancement("minecraft", "recipes/misc/stick"), "the recipe book depends on these");
        assertFalse(VanillaAdvancementListener.isVanillaAdvancement("copperheist", "toast_1"));
        assertFalse(VanillaAdvancementListener.isVanillaAdvancement("someotherplugin", "story/root"));
    }
}
