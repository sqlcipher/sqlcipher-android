/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
** Modified to support SQLite extensions by the SQLite developers: 
** sqlite-dev@sqlite.org.
*/

package net.zetetic.database.sqlcipher;

import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteException;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteProgram;
import androidx.sqlite.db.SupportSQLiteQuery;

import net.zetetic.database.CursorWindow;
import net.zetetic.database.Logger;

/**
 * Represents a query that reads the resulting rows into a {@link SQLiteQuery}.
 * This class is used by {@link SQLiteCursor} and isn't useful itself.
 * <p>
 * This class is not thread-safe.
 * </p>
 */
public final class SQLiteQuery extends SQLiteProgram implements SupportSQLiteQuery {
    private static final String TAG = "SQLiteQuery";

    private final CancellationSignal mCancellationSignal;

    SQLiteQuery(SQLiteDatabase db, String query, CancellationSignal cancellationSignal) {
        super(db, query, null, cancellationSignal);

        mCancellationSignal = cancellationSignal;
    }

    /**
     * Reads rows into a buffer.
     *
     * @param window The window to fill into
     * @param startPos The start position for filling the window.
     * @param requiredPos The position of a row that MUST be in the window.
     * If it won't fit, then the query should discard part of what it filled.
     * @param countAllRows True to count all rows that the query would
     * return regardless of whether they fit in the window.
     * @return Number of rows that were enumerated.  Might not be all rows
     * unless countAllRows is true.
     *
     * @throws SQLiteException if an error occurs.
     * @throws OperationCanceledException if the operation was canceled.
     */
    int fillWindow(CursorWindow window, int startPos, int requiredPos, boolean countAllRows) {
        acquireReference();
        try {
            window.acquireReference();
            try {
                int numRows = getSession().executeForCursorWindow(getSql(), getBindArgs(),
                        window, startPos, requiredPos, countAllRows, getConnectionFlags(),
                        mCancellationSignal);
                return numRows;
            } catch (SQLiteDatabaseCorruptException ex) {
                onCorruption(ex);
                throw ex;
            } catch (SQLiteException ex) {
                Logger.e(TAG, "exception: " + ex.getMessage() + "; query: " + getSql());
                throw ex;
            } finally {
                window.releaseReference();
            }
        } finally {
            releaseReference();
        }
    }

    @Override
    public String toString() {
        return "SQLiteQuery: " + getSql();
    }

    @NonNull
    @Override
    public String getSql() {
        return super.getSql();
    }

    @Override
    public void bindTo(@NonNull SupportSQLiteProgram supportSQLiteProgram) {
        Object[] bindArgs = super.getBindArgs();
        if (bindArgs == null) {
            return;
        }
        for (var i = 0; i < bindArgs.length; i++) {
            var index = i + 1;
            Object arg = bindArgs[i];
            bindArgumentToProgram(supportSQLiteProgram, index, arg);
        }
    }

    @Override
    public int getArgCount() {
        var bindArgs = super.getBindArgs();
        return bindArgs == null
                ? 0
                : bindArgs.length;
    }

    private void bindArgumentToProgram(@NonNull SupportSQLiteProgram program, int index, Object arg) {
        if (arg == null) {
            program.bindNull(index);
        } else if (arg instanceof byte[]) {
            program.bindBlob(index, (byte[]) arg);
        } else if (arg instanceof Float || arg instanceof Double) {
            program.bindDouble(index, ((Number) arg).doubleValue());
        } else if (arg instanceof Boolean) {
            var value = ((Boolean) arg)
                    ? 1L
                    : 0L;
            program.bindLong(index, value);
        } else if (arg instanceof Integer || arg instanceof Long
                || arg instanceof Short || arg instanceof Byte) {
            program.bindLong(index, ((Number) arg).longValue());
        } else if (arg instanceof String) {
            program.bindString(index, (String) arg);
        } else {
            var message = "Cannot bind " + arg + " at index " + index
                    + " supported types: null, byte[], float, double, int, long, boolean, String";
            throw new IllegalArgumentException(message);
        }
    }
}
