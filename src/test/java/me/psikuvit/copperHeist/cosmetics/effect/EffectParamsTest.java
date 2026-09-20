package me.psikuvit.copperHeist.cosmetics.effect;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectParamsTest {

    @Test
    void aPlainParticleParsesWithDefaults() {
        ParticleSpec spec = ParticleSpec.parse(Map.of("particle", "flame"));
        assertEquals(Particle.FLAME, spec.particle());
        assertNull(spec.data());
        assertEquals(1, spec.count());
        assertEquals(0.0, spec.spread());
    }

    @Test
    void dustTakesAColourOrRainbow() {
        ParticleSpec orange = ParticleSpec.parse(Map.of("particle", "DUST", "color", "#FF8800", "size", 2));
        assertEquals(Color.fromRGB(0xFF8800), ((Particle.DustOptions) orange.data()).getColor());
        assertEquals(2.0f, orange.size());
        assertFalse(orange.rainbow());
        assertTrue(ParticleSpec.parse(Map.of("particle", "DUST", "color", "rainbow")).rainbow());
    }

    @Test
    void particlesThatNeedDataNeedTheRightParam() {
        assertNotNull(ParticleSpec.parse(Map.of("particle", "ITEM", "material", "GOLD_NUGGET")).data());
        assertThrows(IllegalArgumentException.class, () -> ParticleSpec.parse(Map.of("particle", "ITEM")));
        assertThrows(IllegalArgumentException.class, () -> ParticleSpec.parse(Map.of("particle", "ITEM", "material", "NOPE")));
    }

    @Test
    void mistakesAreReportedReadably() {
        assertThrows(IllegalArgumentException.class, () -> ParticleSpec.parse(Map.of()), "particle is required");
        assertThrows(IllegalArgumentException.class, () -> ParticleSpec.parse(Map.of("particle", "NOT_A_PARTICLE")));
        assertThrows(IllegalArgumentException.class, () -> ParticleSpec.parse(Map.of("particle", "DUST", "color", "orange")));
        assertThrows(IllegalArgumentException.class, () -> ParticleSpec.parse(Map.of("particle", "FLAME", "count", "many")));
    }

    @Test
    void numbersAreClampedSoNoConfigCanFloodPlayers() {
        ParticleSpec big = ParticleSpec.parse(Map.of("particle", "FLAME", "count", 100000, "spread", 50, "speed", 9));
        assertEquals(50, big.count());
        assertEquals(2.0, big.spread());
        assertEquals(1.0, big.speed());
        assertEquals(1, ParticleSpec.parse(Map.of("particle", "FLAME", "count", -5)).count());
    }

    @Test
    void theRainbowWalksThroughDifferentColours() {
        Set<Color> seen = new HashSet<>();
        for (int tick = 0; tick < 60; tick += 5) seen.add(ParticleSpec.rainbowColor(tick));
        assertTrue(seen.size() >= 10, "colours change along the cycle");
        assertEquals(ParticleSpec.rainbowColor(0), ParticleSpec.rainbowColor(60), "and it loops");
    }

    @Test
    void burstShapesStayInsideTheirSize() {
        List<Vector> ring = ParticleBurstEffect.points("ring", 2, 3, 0);
        assertEquals(24, ring.size());
        for (Vector point : ring) assertEquals(2.0, Math.hypot(point.getX(), point.getZ()), 1e-9);

        for (Vector point : ParticleBurstEffect.points("sphere", 1.5, 3, 0)) {
            assertEquals(1.5, new Vector(point.getX(), point.getY() - 1, point.getZ()).length(), 1e-9);
        }
        for (Vector point : ParticleBurstEffect.points("rain", 1.5, 2.5, 3)) {
            assertTrue(Math.hypot(point.getX(), point.getZ()) <= 1.5 + 1e-9);
            assertEquals(2.5, point.getY());
        }
    }

    @Test
    void theSpiralClimbsOneStepAtATime() {
        double last = -1;
        for (int tick = 0; tick < 20; tick++) {
            List<Vector> points = ParticleBurstEffect.points("spiral", 1, 4, tick);
            assertEquals(2, points.size());
            assertTrue(points.get(0).getY() > last, "rises every tick");
            last = points.get(0).getY();
        }
        assertTrue(last < 4, "and stays below the configured height");
    }

    @Test
    void fireworksParseOrExplainWhyNot() {
        assertNotNull(FireworkBurstEffect.build(Map.of("colors", List.of("#FF0000", "#00FF00"), "type", "STAR", "trail", true)));
        assertNotNull(FireworkBurstEffect.build(Map.of()), "no colours means white");
        assertThrows(IllegalArgumentException.class, () -> FireworkBurstEffect.build(Map.of("type", "TRIANGLE")));
        assertThrows(IllegalArgumentException.class, () -> FireworkBurstEffect.build(Map.of("colors", List.of("red"))));
        assertThrows(IllegalArgumentException.class, () -> FireworkBurstEffect.build(Map.of("colors", "#FF0000")));
    }
}
