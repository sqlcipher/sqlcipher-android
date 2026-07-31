package net.zetetic.database.sqlcipher.driver;

import androidx.annotation.NonNull;
import androidx.sqlite.SQLiteConnection;
import androidx.sqlite.SQLiteDriver;

import net.zetetic.database.DatabaseErrorHandler;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;

public class SQLCipherDriver implements SQLiteDriver {

    private final byte[] passphrase;
    private final SQLiteDatabaseHook hook;
    private final DatabaseErrorHandler handler;

    public SQLCipherDriver(
            byte[] passphrase,
            SQLiteDatabaseHook hook,
            DatabaseErrorHandler handler){
        this.passphrase = passphrase;
        this.hook = hook;
        this.handler = handler;
    }

    @NonNull
    @Override
    public SQLiteConnection open(
            @NonNull String filename) {
        var db = SQLiteDatabase.openOrCreateDatabase(filename, passphrase, null, handler, hook);
        return new SQLCipherConnection(db);
    }
}
