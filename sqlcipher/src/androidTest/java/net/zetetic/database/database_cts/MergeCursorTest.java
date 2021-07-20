/*
 * Copyright (C) 2008 The Android Open Source Project
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

package net.zetetic.database.database_cts;


import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.MergeCursor;
import android.database.StaleDataException;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import android.test.AndroidTestCase;

import java.io.File;
import java.util.Arrays;

public class MergeCursorTest extends AndroidTestCase {
    private final int NUMBER_1_COLUMN_INDEX = 1;
    private static final String TABLE1_NAME = "test1";
    private static final String TABLE2_NAME = "test2";
    private static final String TABLE3_NAME = "test3";
    private static final String TABLE4_NAME = "test4";
    private static final String TABLE5_NAME = "test5";
    private static final String COLUMN_FOR_NULL_TEST = "Null Field";

    private SQLiteDatabase mDatabase;
    private File mDatabaseFile;

    Cursor[] mCursors = null;
    private static final String TABLE1_COLUMNS = " number_1 INTEGER";
    private static final String TABLE2_COLUMNS = " number_1 INTEGER, number_2 INTEGER";
    private static final String TABLE3_COLUMNS = " text_1 TEXT, number_3 INTEGER, number_4 REAL";
    private static final String TABLE2_COLUMN_NAMES = "_id,number_1,number_2";
    private static final String TABLE3_COLUMN_NAMES = "_id,text_1,number_3,number_4";
    private static final String TEXT_COLUMN_NAME = "text_1";
    private static final int TABLE2_COLUMN_COUNT = 3;
    private static final int TABLE3_COLUMN_COUNT = 4;
    private static final int DEFAULT_TABLE_VALUE_BEGINS = 1;
    private static final int MAX_VALUE = 10;
    private static final int HALF_VALUE = MAX_VALUE / 2;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        System.loadLibrary("sqlcipher");
        setupDatabase();
        mCursors = new Cursor[2];
    }

    @Override
    protected void tearDown() throws Exception {
        for (int i = 0; i < mCursors.length; i++) {
            if (null != mCursors[i]) {
                mCursors[i].close();
            }
        }
        mDatabase.close();
        mDatabaseFile.delete();
        super.tearDown();
    }

    public void testConstructor() {
        // If each item of mCursors are null, count will be zero.
        MergeCursor mergeCursor = new MergeCursor(mCursors);
        assertEquals(0, mergeCursor.getCount());

        createCursors();

        // if the items are not null, getCount() will return the sum of all cursors' count.
        mergeCursor = new MergeCursor(mCursors);
        assertEquals(mCursors[0].getCount() + mCursors[1].getCount(), mergeCursor.getCount());
    }

    public void testOnMove() {
        createCursors();
        MergeCursor mergeCursor = new MergeCursor(mCursors);
        for (int i = 0; i < MAX_VALUE; i++) {
            mergeCursor.moveToNext();
            //From 1~5, mCursor should be in mCursors[0], larger than 5, it should be in
            //mCursors[1].
            assertEquals(i + 1, mergeCursor.getInt(NUMBER_1_COLUMN_INDEX));
        }
    }

    public void testCursorSwiching() {
        mDatabase.execSQL("CREATE TABLE " + TABLE5_NAME + " (_id INTEGER PRIMARY KEY,"
                + TABLE3_COLUMNS + ");");
        String sql = "INSERT INTO " + TABLE5_NAME + " (" + TEXT_COLUMN_NAME + ") VALUES ('TEXT')";
        mDatabase.execSQL(sql);

        Cursor[] cursors = new Cursor[2];
        cursors[0] = mDatabase.query(TABLE5_NAME, null, null, null, null, null, null);
        assertEquals(1, cursors[0].getCount());
        createCursors();
        cursors[1] = mCursors[1];
        assertTrue(cursors[1].getCount() > 0);
        MergeCursor mergeCursor = new MergeCursor(cursors);
        // MergeCursor should points to cursors[0] after moveToFirst.
        mergeCursor.moveToFirst();

        String[] tableColumns = TABLE3_COLUMN_NAMES.split("[,]");
        assertEquals(TABLE3_COLUMN_COUNT, mergeCursor.getColumnCount());
        assertTrue(Arrays.equals(tableColumns, mergeCursor.getColumnNames()));

        // MergeCursor should points to cursors[1] moveToNext.
        mergeCursor.moveToNext();
        tableColumns = TABLE2_COLUMN_NAMES.split("[,]");
        assertEquals(TABLE2_COLUMN_COUNT, mergeCursor.getColumnCount());
        assertTrue(Arrays.equals(tableColumns, mergeCursor.getColumnNames()));
    }

    public void testGetValues() {
        byte NUMBER_BLOB_UNIT = 99;
        String[] TEST_STRING = new String[] {"Test String1", "Test String2"};
        String[] tableNames = new String[] {TABLE3_NAME, TABLE4_NAME};

        final double NUMBER_DOUBLE = Double.MAX_VALUE;
        final double NUMBER_FLOAT = (float) NUMBER_DOUBLE;
        final long NUMBER_LONG_INTEGER = (long) 0xaabbccddffL;
        final long NUMBER_INTEGER = (int) NUMBER_LONG_INTEGER;
        final long NUMBER_SHORT = (short) NUMBER_INTEGER;

        // create tables
        byte[][] originalBlobs = new byte[2][];
        for (int i = 0; i < 2; i++) {
            // insert blob and other values
            originalBlobs[i] = new byte[1000];
            Arrays.fill(originalBlobs[i], (byte) (NUMBER_BLOB_UNIT - i));
            buildDatabaseWithTestValues(TEST_STRING[i], NUMBER_DOUBLE - i, NUMBER_LONG_INTEGER - i,
                    originalBlobs[i], tableNames[i]);
            // Get cursors.
            mCursors[i] = mDatabase.query(tableNames[i], null, null, null, null, null, null);
        }

        MergeCursor mergeCursor = new MergeCursor(mCursors);
        assertEquals(4, mergeCursor.getCount());
        String[] testColumns = new String[] {"_id", "string_text", "double_number", "int_number",
                    "blob_data"};
        // Test getColumnNames().
        assertTrue(Arrays.equals(testColumns, mergeCursor.getColumnNames()));

        int columnBlob = mCursors[0].getColumnIndexOrThrow("blob_data");
        int columnString = mCursors[0].getColumnIndexOrThrow("string_text");
        int columnDouble = mCursors[0].getColumnIndexOrThrow("double_number");
        int columnInteger = mCursors[0].getColumnIndexOrThrow("int_number");

        // Test values.
        for (int i = 0; i < 2; i++) {
            mergeCursor.moveToNext();
            assertEquals(5, mergeCursor.getColumnCount());

            // Test getting value methods.
            byte[] targetBlob = mergeCursor.getBlob(columnBlob);
            assertTrue(Arrays.equals(originalBlobs[i], targetBlob));

            assertEquals(TEST_STRING[i], mergeCursor.getString(columnString));
            assertEquals(NUMBER_DOUBLE - i, mergeCursor.getDouble(columnDouble), 0.000000000001);
            assertEquals(NUMBER_FLOAT - i, mergeCursor.getFloat(columnDouble), 0.000000000001f);
            assertEquals(NUMBER_LONG_INTEGER - i, mergeCursor.getLong(columnInteger));
            assertEquals(NUMBER_INTEGER - i, mergeCursor.getInt(columnInteger));
            assertEquals(NUMBER_SHORT - i, mergeCursor.getShort(columnInteger));

            // Test isNull(int).
            assertFalse(mergeCursor.isNull(columnBlob));
            mergeCursor.moveToNext();
            assertEquals(COLUMN_FOR_NULL_TEST, mergeCursor.getString(columnString));
            assertTrue(mergeCursor.isNull(columnBlob));
        }
    }

    public void testContentObsererOperations() throws IllegalStateException {
        createCursors();
        MergeCursor mergeCursor = new MergeCursor(mCursors);
        ContentObserver observer = new ContentObserver(null) {};

        // Can't unregister a Observer before it has been registered.
        try {
            mergeCursor.unregisterContentObserver(observer);
            fail("testUnregisterContentObserver failed");
        } catch (IllegalStateException e) {
            // expected
        }

        mergeCursor.registerContentObserver(observer);

        // Can't register a same observer twice before unregister it.
        try {
            mergeCursor.registerContentObserver(observer);
            fail("testRegisterContentObserver failed");
        } catch (IllegalStateException e) {
            // expected
        }

        mergeCursor.unregisterContentObserver(observer);
        // one Observer can be registered again after it has been unregistered.
        mergeCursor.registerContentObserver(observer);

        mergeCursor.unregisterContentObserver(observer);

        try {
            mergeCursor.unregisterContentObserver(observer);
            fail("testUnregisterContentObserver failed");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testDeactivate() throws IllegalStateException {
        createCursors();
        MergeCursor mergeCursor = new MergeCursor(mCursors);
        MockObserver observer = new MockObserver();

        // one DataSetObserver can't unregistered before it had been registered.
        try {
            mergeCursor.unregisterDataSetObserver(observer);
            fail("testUnregisterDataSetObserver failed");
        } catch (IllegalStateException e) {
            // expected
        }

        // Before registering, observer can't be notified.
        assertFalse(observer.hasInvalidated());
        mergeCursor.moveToLast();
        mergeCursor.deactivate();
        assertFalse(observer.hasInvalidated());

        // Test with registering DataSetObserver
        assertTrue(mergeCursor.requery());
        mergeCursor.registerDataSetObserver(observer);
        assertFalse(observer.hasInvalidated());
        mergeCursor.moveToLast();
        assertEquals(MAX_VALUE, mergeCursor.getInt(NUMBER_1_COLUMN_INDEX));
        mergeCursor.deactivate();
        // deactivate method can invoke invalidate() method, can be observed by DataSetObserver.
        assertTrue(observer.hasInvalidated());
        // After deactivating, the cursor can not provide values from database record.
        try {
            mergeCursor.getInt(NUMBER_1_COLUMN_INDEX);
            fail("After deactivating, cursor cannot execute getting value operations.");
        } catch (StaleDataException e) {
            // expected
        }

        // Can't register a same observer twice before unregister it.
        try {
            mergeCursor.registerDataSetObserver(observer);
            fail("testRegisterDataSetObserver failed");
        } catch (IllegalStateException e) {
            // expected
        }

        // After runegistering, observer can't be notified.
        mergeCursor.unregisterDataSetObserver(observer);
        observer.resetStatus();
        assertFalse(observer.hasInvalidated());
        mergeCursor.moveToLast();
        mergeCursor.deactivate();
        assertFalse(observer.hasInvalidated());

        // one DataSetObserver can't be unregistered twice continuously.
        try {
            mergeCursor.unregisterDataSetObserver(observer);
            fail("testUnregisterDataSetObserver failed");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testRequery() {
        final String TEST_VALUE1 = Integer.toString(MAX_VALUE + 1);
        final String TEST_VALUE2 = Integer.toString(MAX_VALUE + 2);
        createCursors();
        MergeCursor mergeCursor = new MergeCursor(mCursors);
        int cursor1Count = mCursors[0].getCount();
        int cursor2Count = mCursors[0].getCount();

        mDatabase.execSQL("INSERT INTO " + TABLE1_NAME + " (number_1) VALUES ('" + TEST_VALUE1
                + "');");
        assertEquals(cursor1Count + cursor2Count, mergeCursor.getCount());
        assertTrue(mergeCursor.requery());
        cursor1Count += 1;
        assertEquals(cursor1Count + cursor2Count, mergeCursor.getCount());
        mDatabase.execSQL("INSERT INTO " + TABLE2_NAME + " (number_1) VALUES ('" + TEST_VALUE2
                + "');");
        cursor2Count += 1;
        assertTrue(mergeCursor.requery());
        assertEquals(cursor1Count + cursor2Count, mergeCursor.getCount());

        mergeCursor.close();
        assertFalse(mergeCursor.requery());
    }

    private void buildDatabaseWithTestValues(String text, double doubleNumber, long intNumber,
            byte[] blob, String tablename) {
        Object[] args = new Object[4];
        args[0] = text;
        args[1] = doubleNumber;
        args[2] = intNumber;
        args[3] = blob;
        mDatabase.execSQL("CREATE TABLE " + tablename + " (_id INTEGER PRIMARY KEY,"
                + "string_text TEXT, double_number REAL, int_number INTEGER, blob_data BLOB);");

        // Insert record in giving table.
        String sql = "INSERT INTO " + tablename + " (string_text, double_number, int_number,"
                + " blob_data) VALUES (?,?,?,?)";
        mDatabase.execSQL(sql, args);
        // insert null blob.
        sql = "INSERT INTO " + tablename + " (string_text) VALUES ('" + COLUMN_FOR_NULL_TEST + "')";
        mDatabase.execSQL(sql);
    }

    private void setupDatabase() {
        File dbDir = getContext().getDir("tests", Context.MODE_PRIVATE);
        mDatabaseFile = new File(dbDir, "database_test.db");
        if (mDatabaseFile.exists()) {
            mDatabaseFile.delete();
        }
        mDatabase = SQLiteDatabase.openOrCreateDatabase(mDatabaseFile.getPath(), null);
        assertNotNull(mDatabaseFile);
        createTable(TABLE1_NAME, TABLE1_COLUMNS);
        createTable(TABLE2_NAME, TABLE2_COLUMNS);
        addValuesIntoTable(TABLE1_NAME, DEFAULT_TABLE_VALUE_BEGINS, HALF_VALUE);
        addValuesIntoTable(TABLE2_NAME, HALF_VALUE + 1, MAX_VALUE);
    }

    private void createTable(String tableName, String columnNames) {
        String sql = "Create TABLE " + tableName + " (_id INTEGER PRIMARY KEY, " + columnNames
                + " );";
        mDatabase.execSQL(sql);
    }

    private void addValuesIntoTable(String tableName, int start, int end) {
        for (int i = start; i <= end; i++) {
            mDatabase.execSQL("INSERT INTO " + tableName + "(number_1) VALUES ('"
                    + i + "');");
        }
    }

    private Cursor getCursor(String tableName, String selection, String[] columnNames) {
        return mDatabase.query(tableName, columnNames, selection, null, null, null, "number_1");
    }

    private void createCursors() {
        mCursors[0] = getCursor(TABLE1_NAME, null, null);
        mCursors[1] = getCursor(TABLE2_NAME, null, null);
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

        public void resetStatus() {
            mHasChanged = false;
            mHasInvalidated = false;
        }

        public boolean hasChanged() {
            return mHasChanged;
        }

        public boolean hasInvalidated () {
            return mHasInvalidated;
        }
    }
}
