package me.psikuvit.copperHeist.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Where player data lives. {@link SqliteDatabase} and {@link MysqlDatabase} only differ in how they connect and in
 * the upsert syntax; the tables are the same portable SQL. Callers close every connection they get.
 */
public interface Database {

    Connection getConnection() throws SQLException;

    /** Parameters: uuid, stat, amount - adds amount to the stored value, creating the row if needed. */
    String upsertStat();

    /** Parameters: uuid, name, first_seen, last_seen - inserts a player or refreshes their name and last_seen. */
    String upsertPlayer();

    /** Creates the tables if they don't exist yet; also proves the connection works. */
    default void createTables() throws SQLException {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS ch_players ("
                    + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                    + "name VARCHAR(32) NOT NULL, "
                    + "first_seen BIGINT NOT NULL, "
                    + "last_seen BIGINT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS ch_stats ("
                    + "uuid VARCHAR(36) NOT NULL, "
                    + "stat VARCHAR(48) NOT NULL, "
                    + "value BIGINT NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (uuid, stat))");
            statement.execute("CREATE TABLE IF NOT EXISTS ch_matches ("
                    + "match_id VARCHAR(36) NOT NULL PRIMARY KEY, "
                    + "arena VARCHAR(64) NOT NULL, "
                    + "winner VARCHAR(16), "
                    + "copper_score INT NOT NULL, "
                    + "iron_score INT NOT NULL, "
                    + "duration_seconds INT NOT NULL, "
                    + "ended_at BIGINT NOT NULL)");
        }
    }
}
