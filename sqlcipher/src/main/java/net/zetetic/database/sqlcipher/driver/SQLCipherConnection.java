package net.zetetic.database.sqlcipher.driver;

import androidx.annotation.NonNull;
import androidx.sqlite.SQLiteConnection;
import androidx.sqlite.SQLiteStatement;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

public class SQLCipherConnection implements SQLiteConnection {
    private final SQLiteDatabase database;

    public SQLCipherConnection(
            SQLiteDatabase database) {
        this.database = database;
    }

    @NonNull
    @Override
    public SQLiteStatement prepare(@NonNull String sql) {
        return new SQLCipherStatement(database, sql);
    }

    @Override
    public void close() {
        database.close();
    }
}
