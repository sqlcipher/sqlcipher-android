package net.zetetic.database.sqlcipher.driver;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.sqlite.SQLite;
import androidx.sqlite.SQLiteStatement;
import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("resource")
public class SQLCipherStatement implements SQLiteStatement {

    private final SQLiteDatabase database;
    private final String sql;
    private final Map<Integer, Object> bindings = new LinkedHashMap<>();
    private Cursor cursor;
    private boolean stepped;
    private boolean closed;

    public SQLCipherStatement(
            SQLiteDatabase database,
            String sql) {
        this.database = database;
        this.sql = sql;
    }

    @Override
    public void bindBlob(
            int index,
            @NonNull byte[] value) {
        bindings.put(index, value);
    }

    @Override
    public void bindDouble(
            int index,
            double value) {
        bindings.put(index, value);
    }

    @Override
    public void bindLong(
            int index,
            long value) {
        bindings.put(index, value);
    }

    @Override
    public void bindText(
            int index,
            @NonNull String value) {
        bindings.put(index, value);
    }

    @Override
    public void bindNull(
            int index) {
        bindings.put(index, null);
    }

    @NonNull
    @Override
    public byte[] getBlob(
            int index) {
        return requireCursor().getBlob(index);
    }

    @Override
    public double getDouble(
            int index) {
        return requireCursor().getDouble(index);
    }

    @Override
    public long getLong(
            int index) {
        return requireCursor().getLong(index);
    }

    @NonNull
    @Override
    public String getText(
            int index) {
        return requireCursor().getString(index);
    }

    @Override
    public boolean isNull(
            int index) {
        return requireCursor().isNull(index);
    }

    @Override
    public int getColumnCount() {
        if (cursor == null) {
            createCursor();
        }
        return cursor.getColumnCount();
    }

    @NonNull
    @Override
    public String getColumnName(
            int index) {
        if (cursor == null) {
            createCursor();
        }
        return cursor.getColumnName(index);
    }

    @Override
    public int getColumnType(
            int index) {
        return toSqliteDataType(requireCursor().getType(index));
    }

    private static int toSqliteDataType(int cursorFieldType) {
        switch (cursorFieldType) {
            case Cursor.FIELD_TYPE_NULL:
                return SQLite.SQLITE_DATA_NULL;
            case Cursor.FIELD_TYPE_INTEGER:
                return SQLite.SQLITE_DATA_INTEGER;
            case Cursor.FIELD_TYPE_FLOAT:
                return SQLite.SQLITE_DATA_FLOAT;
            case Cursor.FIELD_TYPE_STRING:
                return SQLite.SQLITE_DATA_TEXT;
            case Cursor.FIELD_TYPE_BLOB:
                return SQLite.SQLITE_DATA_BLOB;
            default:
                throw new SQLException("unknown cursor field type " + cursorFieldType);
        }
    }

    @Override
    public boolean step() {
        if(closed){
            throw new SQLException("statement is closed");
        }
        if (cursor == null) {
            var maxIndex = 0;
            for (int key : bindings.keySet()) {
                maxIndex = Math.max(maxIndex, key);
            }
            var args = new Object[maxIndex];
            for (int i = 1; i <= maxIndex; i++) {
                args[i - 1] = bindings.get(i);
            }
            cursor = database.rawQuery(sql, args);
        }
        stepped = cursor.moveToNext();
        return stepped;
    }

    @Override
    public void reset() {
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
        stepped = false;
    }

    @Override
    public void clearBindings() {
        bindings.clear();
    }

    @Override
    public void close() {
        closed = true;
    }

    private void createCursor() {
        var maxIndex = 0;
        for (int key : bindings.keySet()) {
            maxIndex = Math.max(maxIndex, key);
        }
        var args = new Object[maxIndex];
        for (int i = 1; i <= maxIndex; i++) {
            args[i - 1] = bindings.get(i);
        }
        cursor = database.rawQuery(sql, args);
    }

    private Cursor requireCursor() {
        if (!stepped || cursor == null) {
            throw new IllegalStateException("step() must be called before reading column values");
        }
        return cursor;
    }
}
