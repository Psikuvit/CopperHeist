package me.psikuvit.copperHeist.game;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamAssignerTest {

    @Test
    void aSoloPlayerJoinsTheSmallerTeam() {
        assertEquals(Optional.of(Team.COPPER), TeamAssigner.choose(0, 0, 8, 1, null));
        assertEquals(Optional.of(Team.IRON), TeamAssigner.choose(3, 2, 8, 1, null));
        assertEquals(Optional.of(Team.COPPER), TeamAssigner.choose(2, 3, 8, 1, null));
        assertEquals(Optional.of(Team.COPPER), TeamAssigner.choose(4, 4, 8, 1, null), "a tie goes to copper");
    }

    @Test
    void aPartyMatesTeamIsFavouredWhileItHasRoom() {
        assertEquals(Optional.of(Team.IRON), TeamAssigner.choose(1, 5, 8, 1, Team.IRON));
        assertEquals(Optional.of(Team.COPPER), TeamAssigner.choose(5, 1, 8, 1, Team.COPPER));
    }

    @Test
    void aFullPreferredTeamIsNotOverfilled() {
        assertEquals(Optional.of(Team.COPPER), TeamAssigner.choose(2, 8, 8, 1, Team.IRON));
    }

    @Test
    void aPartyNeedsAllItsSeatsOnOneTeam() {
        assertEquals(Optional.of(Team.IRON), TeamAssigner.choose(6, 3, 8, 4, null), "copper has only 2 free seats");
        assertEquals(Optional.of(Team.COPPER), TeamAssigner.choose(4, 4, 8, 4, null));
        assertEquals(Optional.empty(), TeamAssigner.choose(5, 5, 8, 4, null), "3 free seats each is not enough for 4 together");
    }

    @Test
    void nobodyFitsWhenBothTeamsAreFull() {
        assertTrue(TeamAssigner.choose(8, 8, 8, 1, null).isEmpty());
        assertTrue(TeamAssigner.choose(8, 8, 8, 1, Team.COPPER).isEmpty());
    }
}
