package me.psikuvit.copperHeist.game;

/** SETUP..FINAL_RUSH are declared in match order - callers compare ordinals for "this phase or later". */
public enum GameState {
    WAITING,
    STARTING,
    SETUP,
    COLLECTION,
    HEIST,
    FINAL_RUSH,
    ENDING,
    RESETTING
}
