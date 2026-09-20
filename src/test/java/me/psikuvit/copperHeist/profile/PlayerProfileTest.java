package me.psikuvit.copperHeist.profile;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProfileTest {

    private PlayerProfile fresh() {
        return new PlayerProfile(UUID.randomUUID(), PlayerProfile.Data.empty(), true);
    }

    @Test
    void aFreshProfileIsCleanUntilSomethingChanges() {
        PlayerProfile profile = fresh();
        assertFalse(profile.isDirty());
        profile.setField("x", "1");
        assertTrue(profile.isDirty());
    }

    @Test
    void settingTheSameValueAgainIsNotAChange() {
        PlayerProfile profile = fresh();
        profile.setField("x", "1");
        profile.snapshot();
        profile.setField("x", "1");
        assertFalse(profile.isDirty());
    }

    @Test
    void snapshotClearsDirtyAndIsACopy() {
        PlayerProfile profile = fresh();
        profile.unlock("trail_flame");
        PlayerProfile.Data data = profile.snapshot();
        assertFalse(profile.isDirty());
        profile.unlock("trail_heart");
        assertEquals(1, data.unlocks().size(), "the snapshot must not change afterwards");
        assertTrue(profile.isDirty());
    }

    @Test
    void numbersFlagsAndFallbacks() {
        PlayerProfile profile = fresh();
        assertEquals(7, profile.longField("streak", 7));
        profile.setField("streak", 3);
        assertEquals(3, profile.longField("streak", 7));
        profile.setField("streak", "not a number");
        assertEquals(7, profile.longField("streak", 7));

        assertFalse(profile.flag("tutorial"));
        profile.setFlag("tutorial", true);
        assertTrue(profile.flag("tutorial"));
        profile.setFlag("tutorial", false);
        assertFalse(profile.flag("tutorial"));
        assertNull(profile.field("flag.tutorial"));
    }

    @Test
    void youCanOnlyEquipWhatYouOwn() {
        PlayerProfile profile = fresh();
        assertFalse(profile.equip("trail", "flame"));
        assertTrue(profile.unlock("flame"));
        assertFalse(profile.unlock("flame"), "unlocking twice reports no change");
        assertTrue(profile.equip("trail", "flame"));
        assertEquals("flame", profile.equipped("trail"));
    }

    @Test
    void revokingUnequipsToo() {
        PlayerProfile profile = fresh();
        profile.unlock("flame");
        profile.equip("trail", "flame");
        assertTrue(profile.revoke("flame"));
        assertFalse(profile.owns("flame"));
        assertNull(profile.equipped("trail"));
        assertFalse(profile.revoke("flame"));
    }

    @Test
    void loadsExactlyWhatWasSaved() {
        PlayerProfile profile = fresh();
        profile.setField(PlayerProfile.FIRST_JOIN, 1234);
        profile.unlock("a");
        profile.unlock("b");
        profile.equip("trail", "b");
        PlayerProfile.Data data = profile.snapshot();

        PlayerProfile loaded = new PlayerProfile(profile.uuid(), data, false);
        assertFalse(loaded.isNewPlayer());
        assertEquals(1234, loaded.longField(PlayerProfile.FIRST_JOIN, 0));
        assertEquals(Map.of("trail", "b"), loaded.allEquipped());
        assertTrue(loaded.owns("a") && loaded.owns("b"));
        assertFalse(loaded.isDirty());
    }
}
