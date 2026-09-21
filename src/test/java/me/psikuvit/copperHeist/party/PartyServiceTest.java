package me.psikuvit.copperHeist.party;

import me.psikuvit.copperHeist.party.PartyService.AcceptResult;
import me.psikuvit.copperHeist.party.PartyService.Departure;
import me.psikuvit.copperHeist.party.PartyService.InviteResult;
import me.psikuvit.copperHeist.party.PartyService.ManageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyServiceTest {

    private final UUID ann = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID cy = UUID.randomUUID();
    private final UUID dee = UUID.randomUUID();
    private long now;
    private PartyService service;

    @BeforeEach
    void setUp() {
        now = 1_000;
        service = new PartyService(() -> now, () -> 3, () -> 60);
    }

    private void join(UUID leader, UUID member) {
        assertEquals(InviteResult.OK, service.invite(leader, member));
        assertEquals(AcceptResult.OK, service.accept(member));
    }

    @Test
    void invitingAndAcceptingMakesAParty() {
        join(ann, bob);
        PartyInfo party = service.partyOf(bob).orElseThrow();
        assertEquals(ann, party.leader());
        assertEquals(List.of(ann, bob), party.members());
        assertEquals(party, service.partyOf(ann).orElseThrow());
    }

    @Test
    void inviteRulesAreEnforced() {
        assertEquals(InviteResult.SELF, service.invite(ann, ann));
        join(ann, bob);
        assertEquals(InviteResult.NOT_LEADER, service.invite(bob, cy));
        assertEquals(InviteResult.TARGET_IN_PARTY, service.invite(ann, bob));
        assertEquals(InviteResult.OK, service.invite(ann, cy));
        assertEquals(InviteResult.ALREADY_INVITED, service.invite(ann, cy));
    }

    @Test
    void thePartyHasASizeLimit() {
        join(ann, bob);
        join(ann, cy);
        assertEquals(InviteResult.PARTY_FULL, service.invite(ann, dee));
    }

    @Test
    void aFullPartyRefusesLateAcceptsToo() {
        assertEquals(InviteResult.OK, service.invite(ann, bob));
        assertEquals(InviteResult.OK, service.invite(ann, cy));
        assertEquals(InviteResult.OK, service.invite(ann, dee));
        assertEquals(AcceptResult.OK, service.accept(bob));
        assertEquals(AcceptResult.OK, service.accept(cy));
        assertEquals(AcceptResult.PARTY_FULL, service.accept(dee));
    }

    @Test
    void invitesExpire() {
        service.invite(ann, bob);
        now += 61_000;
        assertEquals(AcceptResult.EXPIRED, service.accept(bob));
        assertEquals(AcceptResult.NO_INVITE, service.accept(bob), "an expired invite is gone");
        assertEquals(InviteResult.OK, service.invite(ann, bob), "and can be sent again");
    }

    @Test
    void anInviteJustBeforeExpiryStillWorks() {
        service.invite(ann, bob);
        now += 59_000;
        assertEquals(AcceptResult.OK, service.accept(bob));
    }

    @Test
    void decliningReportsWhoToTell() {
        service.invite(ann, bob);
        assertEquals(Optional.of(ann), service.deny(bob));
        assertEquals(Optional.empty(), service.deny(bob));
        assertEquals(AcceptResult.NO_INVITE, service.accept(bob));
    }

    @Test
    void leavingPassesLeadershipOn() {
        join(ann, bob);
        join(ann, cy);
        Departure departure = service.leave(ann);
        assertTrue(departure.wasInParty());
        assertFalse(departure.disbanded());
        assertEquals(bob, departure.newLeader());
        assertEquals(List.of(bob, cy), departure.remaining());
        assertEquals(bob, service.partyOf(cy).orElseThrow().leader());
        assertTrue(service.partyOf(ann).isEmpty());
    }

    @Test
    void aPartyOfOneEnds() {
        join(ann, bob);
        Departure departure = service.leave(bob);
        assertTrue(departure.disbanded());
        assertEquals(List.of(ann), departure.remaining());
        assertNull(departure.newLeader());
        assertTrue(service.partyOf(ann).isEmpty(), "the leader is free to start or join another party");
        assertEquals(InviteResult.OK, service.invite(bob, ann));
    }

    @Test
    void leavingWhenNotInAPartyDoesNothing() {
        assertFalse(service.leave(ann).wasInParty());
    }

    @Test
    void onlyTheLeaderCanKickPromoteAndDisband() {
        join(ann, bob);
        join(ann, cy);
        assertEquals(ManageResult.NOT_LEADER, service.kick(bob, cy));
        assertEquals(ManageResult.NOT_LEADER, service.promote(bob, cy));
        assertEquals(ManageResult.NOT_LEADER, service.disband(bob));
        assertEquals(ManageResult.NOT_IN_PARTY, service.kick(dee, cy));
        assertEquals(ManageResult.SELF, service.kick(ann, ann));
        assertEquals(ManageResult.TARGET_NOT_MEMBER, service.kick(ann, dee));

        assertEquals(ManageResult.OK, service.kick(ann, cy));
        assertTrue(service.partyOf(cy).isEmpty());
        assertEquals(ManageResult.OK, service.promote(ann, bob));
        assertEquals(bob, service.partyOf(ann).orElseThrow().leader());
    }

    @Test
    void disbandingFreesEveryone() {
        join(ann, bob);
        join(ann, cy);
        assertEquals(ManageResult.OK, service.disband(ann));
        for (UUID id : List.of(ann, bob, cy)) assertTrue(service.partyOf(id).isEmpty());
    }

    @Test
    void anInviteToADisbandedPartyIsGone() {
        join(ann, bob);
        service.invite(ann, cy);
        service.disband(ann);
        assertEquals(AcceptResult.PARTY_GONE, service.accept(cy));
    }

    @Test
    void youCantAcceptWhileInAnotherParty() {
        join(ann, bob);
        service.invite(cy, dee);
        assertEquals(InviteResult.TARGET_IN_PARTY, service.invite(cy, bob));
        assertEquals(AcceptResult.ALREADY_IN_PARTY, service.accept(bob));
    }
}
