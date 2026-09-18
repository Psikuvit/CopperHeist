package me.psikuvit.copperHeist.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** database.type: sqlite - one local file (default plugins/CopperHeist/data.db), no server needed. */
public class SqliteDatabase implements Database {

    private final String url;

    public SqliteDatabase(File file) {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        this.url = "jdbc:sqlite:" + file.getAbsolutePath();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    @Override
    public String upsertStat() {
        return "INSERT INTO ch_stats (uuid, stat, value) VALUES (?, ?, ?) "
                + "ON CONFLICT(uuid, stat) DO UPDATE SET value = value + excluded.value";
    }

    @Override
    public String upsertPlayer() {
        return "INSERT INTO ch_players (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, last_seen = excluded.last_seen";
    }
}
