/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.CursorWrapper;
import android.database.DataSetObserver;
import net.zetetic.database.DatabaseUtils;
import net.zetetic.database.sqlcipher.SQLiteCursor;
import net.zetetic.database.sqlcipher.SQLiteCursorDriver;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteQuery;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.test.PerformanceTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import java.io.File;
import java.util.Arrays;
import java.util.Random;

public class DatabaseCursorTest extends AndroidTestCase implements PerformanceTestCase {
    private static final String sString1 = "this is a test";
    private static final String sString2 = "and yet another test";
    private static final String sString3 = "this string is a little longer, but still a test";

    private static final int CURRENT_DATABASE_VERSION = 42;
    private SQLiteDatabase mDatabase;
    private File mDatabaseFile;
    protected static final int TYPE_CURSOR = 0;
    protected static final int TYPE_CURSORWRAPPER = 1;
    private int  mTestType = TYPE_CURSOR;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        System.loadLibrary("sqlcipher");
        File dbDir = getContext().getDir("tests", Context.MODE_PRIVATE);
        mDatabaseFile = new File(dbDir, "database_test.db");
        if (mDatabaseFile.exists()) {
            mDatabaseFile.delete();
        }
        mDatabase = SQLiteDatabase.openOrCreateDatabase(mDatabaseFile.getPath(), null);
        assertNotNull(mDatabase);
        mDatabase.setVersion(CURRENT_DATABASE_VERSION);
    }

    @Override
    protected void tearDown() throws Exception {
        mDatabase.close();
        mDatabaseFile.delete();
        super.tearDown();
    }

    public void setupTestType(int testType) {
        mTestType = testType;
    }

    private Cursor getTestCursor(Cursor cursor) {
        switch (mTestType) {
        case TYPE_CURSORWRAPPER:
            return new CursorWrapper(cursor);
        case TYPE_CURSOR:
        default:
            return cursor;
        }
    }

    public boolean isPerformanceOnly() {
        return false;
    }

    // These test can only be run once.
    public int startPerformance(Intermediates intermediates) {
        return 1;
    }

    private void populateDefaultTable() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, data TEXT);");

        mDatabase.execSQL("INSERT INTO test (data) VALUES ('" + sString1 + "');");
        mDatabase.execSQL("INSERT INTO test (data) VALUES ('" + sString2 + "');");
        mDatabase.execSQL("INSERT INTO test (data) VALUES ('" + sString3 + "');");
    }

    @MediumTest
    public void testBlob() throws Exception {
        // create table
        mDatabase.execSQL(
                "CREATE TABLE test (_id INTEGER PRIMARY KEY, s TEXT, d REAL, l INTEGER, b BLOB);");
        // insert blob
        Object[] args = new Object[4];

        byte[] blob = new byte[1000];
        byte value = 99;
        Arrays.fill(blob, value);
        args[3] = blob;

        String s = new String("text");
        args[0] = s;
        Double d = 99.9;
        args[1] = d;
        Long l = (long) 1000;
        args[2] = l;

        String sql = "INSERT INTO test (s, d, l, b) VALUES (?,?,?,?)";
        mDatabase.execSQL(sql, args);
        // use cursor to access blob

        Cursor testCursor = mDatabase.query("test", null, null, null, null, null, null);

        testCursor.moveToNext();
        ContentValues cv = new ContentValues();
        DatabaseUtils.cursorRowToContentValues(testCursor, cv);

        int bCol = testCursor.getColumnIndexOrThrow("b");
        int sCol = testCursor.getColumnIndexOrThrow("s");
        int dCol = testCursor.getColumnIndexOrThrow("d");
        int lCol = testCursor.getColumnIndexOrThrow("l");
        byte[] cBlob = testCursor.getBlob(bCol);
        assertTrue(Arrays.equals(blob, cBlob));
        assertEquals(s, testCursor.getString(sCol));
        assertEquals((double) d, testCursor.getDouble(dCol));
        assertEquals((long) l, testCursor.getLong(lCol));
    }

    @MediumTest
    public void testRealColumns() throws Exception {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, data REAL);");
        ContentValues values = new ContentValues();
        values.put("data", 42.11);
        long id = mDatabase.insert("test", "data", values);
        assertTrue(id > 0);
        Cursor testCursor = getTestCursor(mDatabase.rawQuery("SELECT data FROM test", null));
        assertNotNull(testCursor);
        assertTrue(testCursor.moveToFirst());
        assertEquals(42.11, testCursor.getDouble(0));
        testCursor.close();
    }

    @MediumTest
    public void testCursor1() throws Exception {
        populateDefaultTable();

        Cursor testCursor = getTestCursor(mDatabase.query("test", null, null, null, null, null,
                null));

        int dataColumn = testCursor.getColumnIndexOrThrow("data");

        // The cursor should ignore text before the last period when looking for a column. (This
        // is a temporary hack in all implementations of getColumnIndex.)
        int dataColumn2 = testCursor.getColumnIndexOrThrow("junk.data");
        assertEquals(dataColumn, dataColumn2);

        assertSame(3, testCursor.getCount());

        assertTrue(testCursor.isBeforeFirst());

        try {
            testCursor.getInt(0);
            fail("CursorIndexOutOfBoundsException expected");
        } catch (CursorIndexOutOfBoundsException ex) {
            // expected
        }

        testCursor.moveToNext();
        assertEquals(1, testCursor.getInt(0));

        String s = testCursor.getString(dataColumn);
        assertEquals(sString1, s);

        testCursor.moveToNext();
        s = testCursor.getString(dataColumn);
        assertEquals(sString2, s);

        testCursor.moveToNext();
        s = testCursor.getString(dataColumn);
        assertEquals(sString3, s);

        testCursor.moveToPosition(-1);
        testCursor.moveToNext();
        s = testCursor.getString(dataColumn);
        assertEquals(sString1, s);

        testCursor.moveToPosition(2);
        s = testCursor.getString(dataColumn);
        assertEquals(sString3, s);

        int i;

        for (testCursor.moveToFirst(), i = 0; !testCursor.isAfterLast();
                testCursor.moveToNext(), i++) {
            testCursor.getInt(0);
        }

        assertEquals(3, i);

        try {
            testCursor.getInt(0);
            fail("CursorIndexOutOfBoundsException expected");
        } catch (CursorIndexOutOfBoundsException ex) {
            // expected
        }
        testCursor.close();
    }

    @MediumTest
    public void testCursor2() throws Exception {
        populateDefaultTable();

        Cursor testCursor = getTestCursor(mDatabase.query("test", null, "_id > 1000", null, null,
                null, null));
        assertEquals(0, testCursor.getCount());
        assertTrue(testCursor.isBeforeFirst());

        try {
            testCursor.getInt(0);
            fail("CursorIndexOutOfBoundsException expected");
        } catch (CursorIndexOutOfBoundsException ex) {
            // expected
        }

        int i;
        for (testCursor.moveToFirst(), i = 0; !testCursor.isAfterLast();
                testCursor.moveToNext(), i++) {
            testCursor.getInt(0);
        }
        assertEquals(0, i);
        try {
            testCursor.getInt(0);
            fail("CursorIndexOutOfBoundsException expected");
        } catch (CursorIndexOutOfBoundsException ex) {
            // expected
        }
        testCursor.close();
    }

    @MediumTest
    public void testLargeField() throws Exception {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, data TEXT);");

        StringBuilder sql = new StringBuilder(2100);
        sql.append("INSERT INTO test (data) VALUES ('");
        Random random = new Random(System.currentTimeMillis());
        StringBuilder randomString = new StringBuilder(1979);
        for (int i = 0; i < 1979; i++) {
            randomString.append((random.nextInt() & 0xf) % 10);
        }
        sql.append(randomString);
        sql.append("');");
        mDatabase.execSQL(sql.toString());

        Cursor testCursor = getTestCursor(mDatabase.query("test", null, null, null, null, null,
                null));
        assertNotNull(testCursor);
        assertEquals(1, testCursor.getCount());

        assertTrue(testCursor.moveToFirst());
        assertEquals(0, testCursor.getPosition());
        String largeString = testCursor.getString(testCursor.getColumnIndexOrThrow("data"));
        assertNotNull(largeString);
        assertEquals(randomString.toString(), largeString);
        testCursor.close();
    }

    private class TestObserver extends DataSetObserver {
        int total;
        SQLiteCursor c;
        boolean quit = false;

        public TestObserver(int total_, SQLiteCursor cursor) {
            c = cursor;
            total = total_;
        }

        @Override
        public void onChanged() {
            int count = c.getCount();
            if (total == count) {
                int i = 0;
                while (c.moveToNext()) {
                    assertEquals(i, c.getInt(1));
                    i++;
                }
                assertEquals(count, i);
                quit = true;
                Looper.myLooper().quit();
            }
        }

        @Override
        public void onInvalidated() {
        }
    }

    @LargeTest
    public void testManyRowsLong() throws Exception {
        mDatabase.beginTransaction();
        final int count = 9000;
        try {
            mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, data INT);");

            for (int i = 0; i < count; i++) {
                mDatabase.execSQL("INSERT INTO test (data) VALUES (" + i + ");");
            }
            mDatabase.setTransactionSuccessful();
        } finally {
            mDatabase.endTransaction();
        }

        Cursor testCursor = getTestCursor(mDatabase.query("test", new String[] { "data" },
                null, null, null, null, null));
        assertNotNull(testCursor);

        int i = 0;
        while (testCursor.moveToNext()) {
            assertEquals(i, testCursor.getInt(0));
            i++;
        }
        assertEquals(count, i);
        assertEquals(count, testCursor.getCount());

        Log.d("testManyRows", "count " + Integer.toString(i));
        testCursor.close();
    }

    @LargeTest
    public void testManyRowsTxt() throws Exception {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, data TEXT);");
        StringBuilder sql = new StringBuilder(2100);
        sql.append("INSERT INTO test (data) VALUES ('");
        Random random = new Random(System.currentTimeMillis());
        StringBuilder randomString = new StringBuilder(1979);
        for (int i = 0; i < 1979; i++) {
            randomString.append((random.nextInt() & 0xf) % 10);
        }
        sql.append(randomString);
        sql.append("');");

        // if cursor window size changed, adjust this value too
        final int count = 600; // more than two fillWindow needed
        for (int i = 0; i < count; i++) {
            mDatabase.execSQL(sql.toString());
        }

        Cursor testCursor = getTestCursor(mDatabase.query("test", new String[] { "data" }, null,
                null, null, null, null));
        assertNotNull(testCursor);

        int i = 0;
        while (testCursor.moveToNext()) {
            assertEquals(randomString.toString(), testCursor.getString(0));
            i++;
        }
        assertEquals(count, i);
        assertEquals(count, testCursor.getCount());
        testCursor.close();
    }

    @LargeTest
    public void testManyRowsTxtLong() throws Exception {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, txt TEXT, data INT);");

        Random random = new Random(System.currentTimeMillis());
        StringBuilder randomString = new StringBuilder(1979);
        for (int i = 0; i < 1979; i++) {
            randomString.append((random.nextInt() & 0xf) % 10);
        }

        // if cursor window size changed, adjust this value too
        final int count = 600;
        for (int i = 0; i < count; i++) {
            StringBuilder sql = new StringBuilder(2100);
            sql.append("INSERT INTO test (txt, data) VALUES ('");
            sql.append(randomString);
            sql.append("','");
            sql.append(i);
            sql.append("');");
            mDatabase.execSQL(sql.toString());
        }

        Cursor testCursor = getTestCursor(mDatabase.query("test", new String[] { "txt", "data" },
                null, null, null, null, null));
        assertNotNull(testCursor);

        int i = 0;
        while (testCursor.moveToNext()) {
            assertEquals(randomString.toString(), testCursor.getString(0));
            assertEquals(i, testCursor.getInt(1));
            i++;
        }
        assertEquals(count, i);
        assertEquals(count, testCursor.getCount());
        testCursor.close();
    }

    @MediumTest
    public void testRequery() throws Exception {
        populateDefaultTable();

        Cursor testCursor = getTestCursor(mDatabase.rawQuery("SELECT * FROM test", null));
        assertNotNull(testCursor);
        assertEquals(3, testCursor.getCount());
        testCursor.deactivate();
        testCursor.requery();
        assertEquals(3, testCursor.getCount());
        testCursor.close();
    }

    @MediumTest
    public void testRequeryWithSelection() throws Exception {
        populateDefaultTable();

        Cursor testCursor = getTestCursor(
                mDatabase.rawQuery("SELECT data FROM test WHERE data = '" + sString1 + "'",
                null));
        assertNotNull(testCursor);
        assertEquals(1, testCursor.getCount());
        assertTrue(testCursor.moveToFirst());
        assertEquals(sString1, testCursor.getString(0));
        testCursor.deactivate();
        testCursor.requery();
        assertEquals(1, testCursor.getCount());
        assertTrue(testCursor.moveToFirst());
        assertEquals(sString1, testCursor.getString(0));
        testCursor.close();
    }

    @MediumTest
    public void testRequeryWithSelectionArgs() throws Exception {
        populateDefaultTable();

        Cursor testCursor = getTestCursor(mDatabase.rawQuery("SELECT data FROM test WHERE data = ?",
                new String[] { sString1 }));
        assertNotNull(testCursor);
        assertEquals(1, testCursor.getCount());
        assertTrue(testCursor.moveToFirst());
        assertEquals(sString1, testCursor.getString(0));
        testCursor.deactivate();
        testCursor.requery();
        assertEquals(1, testCursor.getCount());
        assertTrue(testCursor.moveToFirst());
        assertEquals(sString1, testCursor.getString(0));
        testCursor.close();
    }

    @MediumTest
    public void testRequeryWithAlteredSelectionArgs() throws Exception {
        /**
         * Test the ability of a subclass of SQLiteCursor to change its query arguments.
         */
        populateDefaultTable();

        SQLiteDatabase.CursorFactory factory = new SQLiteDatabase.CursorFactory() {
            public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
                    String editTable, SQLiteQuery query) {
                return new SQLiteCursor(db, masterQuery, editTable, query) {
                    @Override
                    public boolean requery() {
                        setSelectionArguments(new String[] { "2" });
                        return super.requery();
                    }
                };
            }
        };
        Cursor testCursor = getTestCursor(mDatabase.rawQueryWithFactory(factory,
                "SELECT data FROM test WHERE _id <= ?",
                new String[] { "1" }, null));
        assertNotNull(testCursor);
        assertEquals(1, testCursor.getCount());
        assertTrue(testCursor.moveToFirst());
        assertEquals(sString1, testCursor.getString(0));

        // Our hacked requery() changes the query arguments in the cursor.
        testCursor.requery();

        assertEquals(2, testCursor.getCount());
        assertTrue(testCursor.moveToFirst());
        assertEquals(sString1, testCursor.getString(0));
        assertTrue(testCursor.moveToNext());
        assertEquals(sString2, testCursor.getString(0));

        // Test that setting query args on a deactivated cursor also works.
        testCursor.deactivate();
        testCursor.requery();
    }
}
