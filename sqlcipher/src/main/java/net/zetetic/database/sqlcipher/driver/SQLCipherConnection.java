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

    /**
     * Transform SQL string into SQLiteStatement. We preprocess the SQL with the following rules:
     * - When a journal mode is set, dispatch the operation, specifically when WAL is used we
     * dispatch using the enableWriteAheadLogging which will reconfigure the underlying
     * connection pool.
     * - For all other statements, we return a default SQLCipherStatement for processing.
     */
    @NonNull
    @Override
    public SQLiteStatement prepare(
            @NonNull String sql) {
        var trimmed = sql.trim();
        var prefix = statementPrefix(trimmed);
        if (prefix != null) {
            if (isJournalModeSet(prefix, trimmed)) {
                // Run the pragma normally, then let SQLiteDatabase reconfigure the pool
                return new JournalModeStatement(database, new SQLCipherStatement(database, sql));
            }
        }
        return new SQLCipherStatement(database, sql);
    }

    @Override
    public boolean inTransaction() {
        return database.inTransaction();
    }

    public void changePassword(
            byte[] newPassword){
        database.changePassword(newPassword);
    }

    @Override
    public void close() {
        database.close();
    }

    static boolean isJournalModeSet(
            String prefix,
            String sql) {
        if (!"PRA".equalsIgnoreCase(prefix)) {
            return false;
        }
        var lower = sql.toLowerCase();
        var index = lower.indexOf("journal_mode");
        // Only a *set* ("PRAGMA journal_mode = WAL") needs special handling;
        // a bare query ("PRAGMA journal_mode") can run normally.
        return index >= 0
                && lower.indexOf('=', index) >= 0;
    }

    /**
     * Returns the first 3 significant characters of the statement, skipping
     * whitespace and SQL comments (-- line and slash-star block). Port of
     * <a href="https://github.com/androidx/androidx/blob/androidx-main/sqlite/sqlite/src/commonMain/kotlin/androidx/sqlite/util/SQLStatementParser.kt">androidx.sqlite.util.SQLStatementParser.getStatementPrefix()</a>
     */
    static String statementPrefix(
            String statement) {
        var limit = statement.length() - 2;
        if (limit < 0) {
            return null;
        }
        var index = 0;
        while (index >= 0 && index < limit) {
            var c = statement.charAt(index);
            if (c <= ' ') {
                index++;
            } else if (c == '-') {
                if (statement.charAt(index + 1) != '-') {
                    break;
                }
                index = statement.indexOf('\n', index + 2);
                if (index < 0) {
                    return null;
                }
                index++;
            } else if (c == '/') {
                if (statement.charAt(index + 1) != '*') {
                    break;
                }
                index++;
                do {
                    index = statement.indexOf('*', index + 1);
                    if (index < 0) {
                        return null;
                    }
                } while (index + 1 < limit && statement.charAt(index + 1) != '/');
                index += 2;
            } else {
                break;
            }
        }
        if (index < 0 || index >= statement.length()) {
            return null;
        }
        return statement.substring(index, Math.min(index + 3, statement.length()));
    }
}
