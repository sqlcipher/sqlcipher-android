/*
 * Copyright (C) 2009 The Android Open Source Project
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

package net.zetetic.database.sqlcipher_cts;


import android.database.AbstractCursor;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.DataSetObserver;
import android.database.StaleDataException;
import android.test.AndroidTestCase;

import net.zetetic.database.sqlcipher.SQLiteCursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDirectCursorDriver;

import java.io.File;
import java.util.Arrays;

/**
 * Test {@link AbstractCursor}.
 */
public class SQLiteCursorTest extends AndroidTestCase {
    private SQLiteDatabase mDatabase;
    private static final String[] COLUMNS = new String[] { "_id", "number_1", "number_2" };
    private static final String TABLE_NAME = "test";
    private static final String TABLE_COLUMNS = " number_1 INTEGER, number_2 INTEGER";
    private static final int DEFAULT_TABLE_VALUE_BEGINS = 1;
    private static final int TEST_COUNT = 10;
    private static final String TEST_SQL = "SELECT * FROM test ORDER BY number_1";
    private static final String DATABASE_FILE = "database_test.db";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        System.loadLibrary("sqlcipher");
        File f = mContext.getDatabasePath(DATABASE_FILE);
        f.mkdirs();
        if (f.exists()) { f.delete(); }
        mDatabase = SQLiteDatabase.openOrCreateDatabase(f, null);
        createTable(TABLE_NAME, TABLE_COLUMNS);
        addValuesIntoTable(TABLE_NAME, DEFAULT_TABLE_VALUE_BEGINS, TEST_COUNT);
    }

    @Override
    protected void tearDown() throws Exception {
        mDatabase.close();
        getContext().deleteDatabase(DATABASE_FILE);
        super.tearDown();
    }

    public void testConstructor() {
        SQLiteDirectCursorDriver cursorDriver = new SQLiteDirectCursorDriver(mDatabase,
                TEST_SQL, TABLE_NAME, null);
        try {
            new SQLiteCursor(mDatabase, cursorDriver, TABLE_NAME, null);
            fail("constructor didn't throw IllegalArgumentException when SQLiteQuery is null");
        } catch (IllegalArgumentException e) {
        }

        // get SQLiteCursor by querying database
        SQLiteCursor cursor = getCursor();
        assertNotNull(cursor);
    }

    public void testClose() {
        SQLiteCursor cursor = getCursor();
        assertTrue(cursor.moveToFirst());
        assertFalse(cursor.isClosed());
        assertTrue(cursor.requery());
        cursor.close();
        assertFalse(cursor.requery());
        try {
            cursor.moveToFirst();
            fail("moveToFirst didn't throw IllegalStateException after closed.");
        } catch (IllegalStateException e) {
        }
        assertTrue(cursor.isClosed());
    }

    public void testRegisterDataSetObserver() {
        SQLiteCursor cursor = getCursor();
        MockCursorWindow cursorWindow = new MockCursorWindow(false);

        MockObserver observer = new MockObserver();

        cursor.setWindow(cursorWindow);
        // Before registering, observer can't be notified.
        assertFalse(observer.hasInvalidated());
        cursor.moveToLast();
        assertFalse(cursorWindow.isClosed());
        cursor.deactivate();
        assertFalse(observer.hasInvalidated());
        // deactivate() will close the CursorWindow
        assertTrue(cursorWindow.isClosed());

        // test registering DataSetObserver
        assertTrue(cursor.requery());
        cursor.registerDataSetObserver(observer);
        assertFalse(observer.hasInvalidated());
        cursor.moveToLast();
        assertEquals(TEST_COUNT, cursor.getInt(1));
        cursor.deactivate();
        // deactivate method can invoke invalidate() method, can be observed by DataSetObserver.
        assertTrue(observer.hasInvalidated());

        try {
            cursor.getInt(1);
            fail("After deactivating, cursor cannot execute getting value operations.");
        } catch (StaleDataException e) {
        }

        assertTrue(cursor.requery());
        cursor.moveToLast();
        assertEquals(TEST_COUNT, cursor.getInt(1));

        // can't register a same observer twice.
        try {
            cursor.registerDataSetObserver(observer);
            fail("didn't throw IllegalStateException when register existed observer");
        } catch (IllegalStateException e) {
        }

        // after unregistering, observer can't be notified.
        cursor.unregisterDataSetObserver(observer);
        observer.resetStatus();
        assertFalse(observer.hasInvalidated());
        cursor.deactivate();
        assertFalse(observer.hasInvalidated());
    }

    public void testRequery() {
        final String DELETE = "DELETE FROM " + TABLE_NAME + " WHERE number_1 =";
        final String DELETE_1 = DELETE + "1;";
        final String DELETE_2 = DELETE + "2;";

        mDatabase.execSQL(DELETE_1);
        // when cursor is created, it refreshes CursorWindow and populates cursor count
        SQLiteCursor cursor = getCursor();
        MockObserver observer = new MockObserver();
        cursor.registerDataSetObserver(observer);
        assertEquals(TEST_COUNT - 1, cursor.getCount());
        assertFalse(observer.hasChanged());

        mDatabase.execSQL(DELETE_2);
        // when getCount() has invoked once, it can no longer refresh CursorWindow.
        assertEquals(TEST_COUNT - 1, cursor.getCount());

        assertTrue(cursor.requery());
        // only after requery, getCount can get most up-to-date counting info now.
        assertEquals(TEST_COUNT - 2, cursor.getCount());
        assertTrue(observer.hasChanged());
    }

    public void testRequery2() {
        mDatabase.disableWriteAheadLogging();
        mDatabase.execSQL("create table testRequery2 (i int);");
        mDatabase.execSQL("insert into testRequery2 values(1);");
        mDatabase.execSQL("insert into testRequery2 values(2);");
        Cursor c = mDatabase.rawQuery("select * from testRequery2 order by i", null);
        assertEquals(2, c.getCount());
        assertTrue(c.moveToFirst());
        assertEquals(1, c.getInt(0));
        assertTrue(c.moveToNext());
        assertEquals(2, c.getInt(0));
        // add more data to the table and requery
        mDatabase.execSQL("insert into testRequery2 values(3);");
        assertTrue(c.requery());
        assertEquals(3, c.getCount());
        assertTrue(c.moveToFirst());
        assertEquals(1, c.getInt(0));
        assertTrue(c.moveToNext());
        assertEquals(2, c.getInt(0));
        assertTrue(c.moveToNext());
        assertEquals(3, c.getInt(0));
        // close the database and see if requery throws an exception
        mDatabase.close();
        assertFalse(c.requery());
    }

    public void testGetColumnIndex() {
        SQLiteCursor cursor = getCursor();

        for (int i = 0; i < COLUMNS.length; i++) {
            assertEquals(i, cursor.getColumnIndex(COLUMNS[i]));
        }

        assertTrue(Arrays.equals(COLUMNS, cursor.getColumnNames()));
    }

    public void testSetSelectionArguments() {
        final String SELECTION = "_id > ?";
        int TEST_ARG1 = 2;
        int TEST_ARG2 = 5;
        SQLiteCursor cursor = (SQLiteCursor) mDatabase.query(TABLE_NAME, null, SELECTION,
                new String[] { Integer.toString(TEST_ARG1) }, null, null, null);
        assertEquals(TEST_COUNT - TEST_ARG1, cursor.getCount());
        cursor.setSelectionArguments(new String[] { Integer.toString(TEST_ARG2) });
        cursor.requery();
        assertEquals(TEST_COUNT - TEST_ARG2, cursor.getCount());
    }

    public void testOnMove() {
        // Do not test this API. It is callback which:
        // 1. The callback mechanism has been tested in super class
        // 2. The functionality is implementation details, no need to test
    }

    private void createTable(String tableName, String columnNames) {
        String sql = "Create TABLE " + tableName + " (_id INTEGER PRIMARY KEY, "
                + columnNames + " );";
        mDatabase.execSQL(sql);
    }

    private void addValuesIntoTable(String tableName, int start, int end) {
        for (int i = start; i <= end; i++) {
            mDatabase.execSQL("INSERT INTO " + tableName + "(number_1) VALUES ('" + i + "');");
        }
    }

    private SQLiteCursor getCursor() {
        SQLiteCursor cursor = (SQLiteCursor) mDatabase.query(TABLE_NAME, null, null,
                null, null, null, null);
        return cursor;
    }

    private class MockObserver extends DataSetObserver {
        private boolean mHasChanged = false;
        private boolean mHasInvalidated = false;

        @Override
        public void onChanged() {
            super.onChanged();
            mHasChanged = true;
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
            mHasInvalidated = true;
        }

        protected void resetStatus() {
            mHasChanged = false;
            mHasInvalidated = false;
        }

        protected boolean hasChanged() {
            return mHasChanged;
        }

        protected boolean hasInvalidated () {
            return mHasInvalidated;
        }
    }

    private class MockCursorWindow extends CursorWindow {
        private boolean mIsClosed = false;

        public MockCursorWindow(boolean localWindow) {
            super(localWindow);
        }

        @Override
        public void close() {
            super.close();
            mIsClosed = true;
        }

        public boolean isClosed() {
            return mIsClosed;
        }
    }
}
