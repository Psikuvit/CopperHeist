package me.psikuvit.copperHeist.profile;

import me.psikuvit.copperHeist.database.SqliteDatabase;
import me.psikuvit.copperHeist.stats.Stat;
import me.psikuvit.copperHeist.stats.StatsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the profile SQL against a real (temporary) SQLite file, including on top of an older database that has no profile tables. */
class ProfileRepositoryTest {

    @TempDir
    Path folder;

    private SqliteDatabase database;
    private StatsRepository stats;
    private ProfileRepository profiles;

    @BeforeEach
    void open() throws Exception {
        database = new SqliteDatabase(new File(folder.toFile(), "test.db"));
        database.createTables();
        stats = new StatsRepository(database);
        profiles = new ProfileRepository(stats);
    }

    @AfterEach
    void close() {
        stats.close();
    }

    @Test
    void anUnknownPlayerHasAnEmptyProfile() throws Exception {
        PlayerProfile.Data data = profiles.load(UUID.randomUUID()).get();
        assertTrue(data.fields().isEmpty() && data.unlocks().isEmpty() && data.equipped().isEmpty());
    }

    @Test
    void savedDataComesBackExactly() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, PlayerProfile.Data.empty(), true);
        profile.setField(PlayerProfile.FIRST_JOIN, 111);
        profile.setFlag("tutorial", true);
        profile.unlock("trail_flame");
        profile.unlock("kill_lightning");
        profile.equip("trail", "trail_flame");
        profiles.save(uuid, profile.snapshot()).get();

        PlayerProfile.Data loaded = profiles.load(uuid).get();
        assertEquals("111", loaded.fields().get(PlayerProfile.FIRST_JOIN));
        assertEquals("1", loaded.fields().get("flag.tutorial"));
        assertEquals(2, loaded.unlocks().size());
        assertEquals(Map.of("trail", "trail_flame"), loaded.equipped());
    }

    @Test
    void savingReplacesInsteadOfMerging() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, PlayerProfile.Data.empty(), true);
        profile.unlock("a");
        profile.unlock("b");
        profile.equip("trail", "a");
        profiles.save(uuid, profile.snapshot()).get();

        profile.revoke("a");
        profiles.save(uuid, profile.snapshot()).get();

        PlayerProfile.Data loaded = profiles.load(uuid).get();
        assertEquals(1, loaded.unlocks().size());
        assertTrue(loaded.unlocks().containsKey("b"));
        assertTrue(loaded.equipped().isEmpty());
    }

    @Test
    void profilesOfDifferentPlayersStaySeparate() throws Exception {
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        PlayerProfile a = new PlayerProfile(one, PlayerProfile.Data.empty(), true);
        a.unlock("only-one");
        profiles.save(one, a.snapshot()).get();
        profiles.save(two, PlayerProfile.Data.empty()).get();

        assertTrue(profiles.load(one).get().unlocks().containsKey("only-one"));
        assertTrue(profiles.load(two).get().unlocks().isEmpty());
    }

    @Test
    void deletingClearsEverything() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, PlayerProfile.Data.empty(), true);
        profile.setField("x", "1");
        profile.unlock("a");
        profiles.save(uuid, profile.snapshot()).get();
        profiles.delete(uuid).get();
        PlayerProfile.Data loaded = profiles.load(uuid).get();
        assertTrue(loaded.fields().isEmpty() && loaded.unlocks().isEmpty());
    }

    @Test
    void profileTablesAreAddedToADatabaseThatAlreadyHasStats() throws Exception {
        // Stats written before the profile tables existed must survive createTables running again (an upgrade).
        UUID uuid = UUID.randomUUID();
        stats.addAll(uuid, Map.of(Stat.WINS, 5L, Stat.XP, 300L)).get();
        database.createTables();
        assertEquals(300L, stats.load(uuid).get().get(Stat.XP));
        assertTrue(profiles.load(uuid).get().fields().isEmpty());
    }

    @Test
    void coinsAddUpAcrossServersSharingTheDatabase() throws Exception {
        // Two servers writing deltas for the same player: nothing is overwritten, both land.
        UUID uuid = UUID.randomUUID();
        StatsRepository other = new StatsRepository(database);
        try {
            stats.addAll(uuid, Map.of(Stat.COINS, 50L)).get();
            other.addAll(uuid, Map.of(Stat.COINS, -20L)).get();
            assertEquals(30L, stats.load(uuid).get().get(Stat.COINS));
        } finally {
            other.close();
        }
    }
}
