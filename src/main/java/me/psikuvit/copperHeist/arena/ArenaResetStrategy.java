package me.psikuvit.copperHeist.arena;

/** How an arena is put back after a match (and cleaned at startup). Chosen by reset.method. */
public interface ArenaResetStrategy {

    void reset(Arena arena);
}
