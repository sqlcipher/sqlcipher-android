package net.zetetic.database.sqlcipher.driver;

import androidx.annotation.NonNull;
import androidx.sqlite.SQLiteStatement;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

class JournalModeStatement implements SQLiteStatement {

    private final SQLiteDatabase database;
    private final SQLiteStatement delegate;

    JournalModeStatement(
            SQLiteDatabase database,
            SQLiteStatement delegate) {
        this.database = database;
        this.delegate = delegate;
    }

    /**
     * Perform journal mode get/set operation. When in WAL
     * mode, we dispatch to enableWriteAheadLogging which
     * will reconfigure the connection pool accordingly.
     */
    @Override
    public boolean step() {
        var hasRow = delegate.step();
        // The pragma returns the mode actually in effect.
        if ("wal".equalsIgnoreCase(delegate.getText(0))) {
            database.enableWriteAheadLogging();
        } else {
            database.disableWriteAheadLogging();
        }
        return hasRow;
    }

    @Override public void bindBlob(
            int index,
            @NonNull byte[] value) {
        delegate.bindBlob(index, value);
    }

    @Override public void bindDouble(
            int index,
            double value) {
        delegate.bindDouble(index, value);
    }

    @Override public void bindLong(
            int index,
            long value) {
        delegate.bindLong(index, value);
    }

    @Override public void bindText(
            int index,
            @NonNull String value) {
        delegate.bindText(index, value);
    }

    @Override public void bindNull(
            int index) {
        delegate.bindNull(index);
    }

    @Override @NonNull public byte[] getBlob(
            int index) {
        return delegate.getBlob(index);
    }

    @Override public double getDouble(
            int index) {
        return delegate.getDouble(index);
    }

    @Override public long getLong(
            int index) {
        return delegate.getLong(index);
    }

    @Override @NonNull public String getText(
            int index) {
        return delegate.getText(index);
    }

    @Override public boolean isNull(
            int index) {
        return delegate.isNull(index);
    }

    @Override public int getColumnCount() {
        return delegate.getColumnCount();
    }

    @Override @NonNull public String getColumnName(
            int index) {
        return delegate.getColumnName(index);
    }

    @Override public int getColumnType(
            int index) {
        return delegate.getColumnType(index);
    }

    @Override public void reset() {
        delegate.reset();
    }

    @Override public void clearBindings() {
        delegate.clearBindings();
    }

    @Override public void close() {
        delegate.close();
    }
}
