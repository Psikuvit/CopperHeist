package me.psikuvit.copperHeist.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * The built-in party state and its rules - no Bukkit in here, so it is unit-tested. A party is created when its leader sends the first
 * invite. Invites expire lazily: they are checked when someone accepts or invites, so nothing has to tick.
 */
public class PartyService {

    public enum InviteResult { OK, SELF, NOT_LEADER, TARGET_IN_PARTY, PARTY_FULL, ALREADY_INVITED }

    public enum AcceptResult { OK, ALREADY_IN_PARTY, NO_INVITE, EXPIRED, PARTY_GONE, PARTY_FULL }

    public enum ManageResult { OK, NOT_IN_PARTY, NOT_LEADER, SELF, TARGET_NOT_MEMBER }

    /** What is left after someone left: whether the party ended, who leads it now (null if unchanged) and who remains. */
    public record Departure(boolean wasInParty, boolean disbanded, UUID newLeader, List<UUID> remaining) {
    }

    private static final class Party {
        UUID leader;
        final Set<UUID> members = new LinkedHashSet<>();

        Party(UUID leader) {
            this.leader = leader;
            members.add(leader);
        }
    }

    private record Invite(Party party, long expiresAt) {
    }

    private final Map<UUID, Party> byMember = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final LongSupplier clock;
    private final IntSupplier maxSize;
    private final IntSupplier inviteSeconds;

    public PartyService(LongSupplier clock, IntSupplier maxSize, IntSupplier inviteSeconds) {
        this.clock = clock;
        this.maxSize = maxSize;
        this.inviteSeconds = inviteSeconds;
    }

    public Optional<PartyInfo> partyOf(UUID player) {
        Party party = byMember.get(player);
        return party == null ? Optional.empty() : Optional.of(info(party));
    }

    public InviteResult invite(UUID inviter, UUID target) {
        if (inviter.equals(target)) return InviteResult.SELF;
        Party party = byMember.get(inviter);
        if (party != null && !party.leader.equals(inviter)) return InviteResult.NOT_LEADER;
        if (byMember.containsKey(target)) return InviteResult.TARGET_IN_PARTY;
        if ((party == null ? 1 : party.members.size()) >= maxSize.getAsInt()) return InviteResult.PARTY_FULL;
        Invite existing = invites.get(target);
        if (existing != null && existing.party == party && party != null && existing.expiresAt > clock.getAsLong()) {
            return InviteResult.ALREADY_INVITED;
        }
        if (party == null) {
            party = new Party(inviter);
            byMember.put(inviter, party);
        }
        invites.put(target, new Invite(party, clock.getAsLong() + inviteSeconds.getAsInt() * 1000L));
        return InviteResult.OK;
    }

    /** The leader of the party this player has been invited to (even if it has expired), or empty. */
    public Optional<UUID> inviterOf(UUID target) {
        Invite invite = invites.get(target);
        return invite == null ? Optional.empty() : Optional.of(invite.party.leader);
    }

    public AcceptResult accept(UUID player) {
        if (byMember.containsKey(player)) return AcceptResult.ALREADY_IN_PARTY;
        Invite invite = invites.get(player);
        if (invite == null) return AcceptResult.NO_INVITE;
        invites.remove(player);
        if (invite.expiresAt <= clock.getAsLong()) return AcceptResult.EXPIRED;
        if (invite.party.members.isEmpty() || byMember.get(invite.party.leader) != invite.party) return AcceptResult.PARTY_GONE;
        if (invite.party.members.size() >= maxSize.getAsInt()) return AcceptResult.PARTY_FULL;
        invite.party.members.add(player);
        byMember.put(player, invite.party);
        return AcceptResult.OK;
    }

    /** Turns down an invite; returns the party leader to tell, or empty when there was nothing pending. */
    public Optional<UUID> deny(UUID player) {
        Invite invite = invites.remove(player);
        return invite == null || invite.expiresAt <= clock.getAsLong() ? Optional.empty() : Optional.of(invite.party.leader);
    }

    public Departure leave(UUID player) {
        invites.remove(player);
        Party party = byMember.remove(player);
        if (party == null) return new Departure(false, false, null, List.of());
        party.members.remove(player);
        if (party.members.size() <= 1) {
            List<UUID> remaining = new ArrayList<>(party.members);
            for (UUID id : remaining) byMember.remove(id);
            party.members.clear();
            return new Departure(true, true, null, remaining);
        }
        UUID newLeader = null;
        if (party.leader.equals(player)) {
            party.leader = party.members.iterator().next();
            newLeader = party.leader;
        }
        return new Departure(true, false, newLeader, new ArrayList<>(party.members));
    }

    public ManageResult kick(UUID leader, UUID target) {
        ManageResult check = checkLeader(leader);
        if (check != ManageResult.OK) return check;
        if (leader.equals(target)) return ManageResult.SELF;
        Party party = byMember.get(leader);
        if (!party.members.contains(target)) return ManageResult.TARGET_NOT_MEMBER;
        leave(target);
        return ManageResult.OK;
    }

    public ManageResult promote(UUID leader, UUID target) {
        ManageResult check = checkLeader(leader);
        if (check != ManageResult.OK) return check;
        if (leader.equals(target)) return ManageResult.SELF;
        Party party = byMember.get(leader);
        if (!party.members.contains(target)) return ManageResult.TARGET_NOT_MEMBER;
        party.leader = target;
        return ManageResult.OK;
    }

    public ManageResult disband(UUID leader) {
        ManageResult check = checkLeader(leader);
        if (check != ManageResult.OK) return check;
        Party party = byMember.get(leader);
        for (UUID id : new ArrayList<>(party.members)) byMember.remove(id);
        party.members.clear();
        return ManageResult.OK;
    }

    private ManageResult checkLeader(UUID player) {
        Party party = byMember.get(player);
        if (party == null) return ManageResult.NOT_IN_PARTY;
        return party.leader.equals(player) ? ManageResult.OK : ManageResult.NOT_LEADER;
    }

    private static PartyInfo info(Party party) {
        return new PartyInfo(party.leader, new ArrayList<>(party.members));
    }
}
