package me.psikuvit.copperHeist.progress;

/**
 * How much XP each level takes. Going from level L to L+1 costs {@code base + step * (L - 1)}, so early levels come quickly and
 * later ones stretch out. Pure maths with no server types, so it is easy to test and to tune.
 */
public final class LevelCurve {

    private final long base;
    private final long step;
    private final int maxLevel;

    public LevelCurve(long base, long step, int maxLevel) {
        this.base = Math.max(1, base);
        this.step = Math.max(0, step);
        this.maxLevel = Math.max(1, maxLevel);
    }

    public int maxLevel() {
        return maxLevel;
    }

    /** XP needed to get from {@code level} to the next one. */
    public long xpForNext(int level) {
        return base + step * (Math.max(1, level) - 1);
    }

    /** Total XP a player needs to have reached {@code level} (level 1 needs none). */
    public long totalFor(int level) {
        long n = Math.max(1, level) - 1;
        return n * base + step * (n * (n - 1) / 2);
    }

    /** The level a player with this much total XP is on, capped at the maximum. */
    public int levelFor(long xp) {
        int level = 1;
        while (level < maxLevel && xp >= totalFor(level + 1)) level++;
        return level;
    }

    /** XP earned inside the current level. At the max level it is always 0. */
    public long xpIntoLevel(long xp) {
        int level = levelFor(xp);
        return level >= maxLevel ? 0 : xp - totalFor(level);
    }

    /** Progress through the current level, 0.0 to 1.0 (1.0 at the max level). */
    public double progress(long xp) {
        int level = levelFor(xp);
        if (level >= maxLevel) return 1.0;
        return (double) (xp - totalFor(level)) / xpForNext(level);
    }
}
