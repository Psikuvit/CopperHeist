package me.psikuvit.copperHeist.ui;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Supplies one scoreboard's content: a title plus its lines. SidebarService
 * renders whichever context currently applies to a player - the server hub
 * board before they've joined an arena, or a match's own board once they
 * have - through the same render() call either way.
 */
public interface ScoreboardContext {

    Component getTitle();

    List<Component> getLines();

    static ScoreboardContext of(Component title, List<Component> lines) {
        return new ScoreboardContext() {
            @Override
            public Component getTitle() {
                return title;
            }

            @Override
            public List<Component> getLines() {
                return lines;
            }
        };
    }
}
