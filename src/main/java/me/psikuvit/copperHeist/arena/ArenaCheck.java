package me.psikuvit.copperHeist.arena;

import me.psikuvit.copperHeist.ui.Text;

/** One line of "arena validate": fine, worth fixing, or blocking enable. */
public record ArenaCheck(Level level, Text text) {

    public enum Level {
        OK, WARN, ERROR
    }
}
