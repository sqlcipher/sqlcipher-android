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
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.DataSetObserver;
import android.database.StaleDataException;
import android.os.Bundle;
import android.os.Handler;
import android.test.AndroidTestCase;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.io.File;
import java.util.Arrays;

public class CursorWrapperTest extends AndroidTestCase {

    private static final String FIRST_NUMBER = "123";
    private static final String SECOND_NUMBER = "5555";
    private static final int TESTVALUE1 = 199;
    private static final int TESTVALUE2 = 200;
    private static final String[] NUMBER_PROJECTION = new String[] {
        "_id",             // 0
        "number"           // 1
    };
    private static final int DEFAULT_RECORD_COUNT = 2;
    private static final int DEFAULT_COLUMN_COUNT = 2;

    private SQLiteDatabase mDatabase;
    private File mDatabaseFile;
    private Cursor mCursor;

    private static final int CURRENT_DATABASE_VERSION = 42;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        System.loadLibrary("sqlcipher");
        setupDatabase();
    }

    @Override
    protected void tearDown() throws Exception {
        closeDatabase();
        super.tearDown();
    }

    public void testConstrucotorAndClose() {
        CursorWrapper cursorWrapper = new CursorWrapper(getCursor());

        assertTrue(cursorWrapper.requery());
        cursorWrapper.deactivate();
        cursorWrapper.move(1);
        assertEquals(DEFAULT_RECORD_COUNT, cursorWrapper.getCount());

        assertFalse(cursorWrapper.isClosed());
        assertTrue(cursorWrapper.requery());
        cursorWrapper.close();
        assertTrue(cursorWrapper.isClosed());
        assertFalse(cursorWrapper.requery());
    }

    private Cursor getCursor() {
        Cursor cursor = mDatabase.query("test1", NUMBER_PROJECTION, null, null, null, null, null);
        return cursor;
    }

    public void testGetCount() {
        CursorWrapper cursorWrapper = new CursorWrapper(getCursor());
        int defaultCount = cursorWrapper.getCount();

        // Add two records into the table.
        addWithValue(mDatabase, TESTVALUE1);
        int expected = defaultCount + 1;
        assertTrue(cursorWrapper.requery());
        assertEquals(expected, cursorWrapper.getCount());
        addWithValue(mDatabase, TESTVALUE2);
        expected += 1;
        assertTrue(cursorWrapper.requery());
        assertEquals(expected, cursorWrapper.getCount());

        // Delete previous two records which have been added just now.
        deleteWithValue(mDatabase, TESTVALUE1);
        assertTrue(cursorWrapper.requery());
        assertEquals(defaultCount + 1, cursorWrapper.getCount());
        deleteWithValue(mDatabase, TESTVALUE2);
        assertTrue(cursorWrapper.requery());
        assertEquals(defaultCount, cursorWrapper.getCount());

        // Continue to delete all the records
        deleteAllRecords(mDatabase);
        assertTrue(cursorWrapper.requery());
        assertEquals(0, cursorWrapper.getCount());

        cursorWrapper.close();
        assertFalse(cursorWrapper.requery());

        // Restore original database status
        rebuildDatabase();
    }

    public void testDeactivate() throws IllegalStateException {
        CursorWrapper cursorWrapper = new CursorWrapper(getCursor());
        MockObserver observer = new MockObserver();

        // one DataSetObserver can't unregistered before it had been registered.
        try{
            cursorWrapper.unregisterDataSetObserver(observer);
            fail("testUnregisterDataSetObserver failed");
        }catch(IllegalStateException e){
        }

        // Before registering, observer can't be notified.
        assertFalse(observer.hasInvalidated());
        cursorWrapper.moveToLast();
        cursorWrapper.deactivate();
        assertFalse(observer.hasInvalidated());

        // Test with registering DataSetObserver
        assertTrue(cursorWrapper.requery());
        cursorWrapper.registerDataSetObserver(observer);
        assertFalse(observer.hasInvalidated());
        cursorWrapper.moveToLast();
        assertEquals(Integer.parseInt(SECOND_NUMBER), cursorWrapper.getInt(1));
        cursorWrapper.deactivate();
        // deactivate method can invoke invalidate() method, can be observed by DataSetObserver.
        assertTrue(observer.hasInvalidated());
        // After deactivating, the cursor can not provide values from database record.
        try {
            cursorWrapper.getInt(1);
            fail("After deactivating, cursor cannot execute getting value operations.");
        } catch (StaleDataException e) {
        }

        // Can't register a same observer twice before unregister it.
        try{
            cursorWrapper.registerDataSetObserver(observer);
            fail("testRegisterDataSetObserver failed");
        }catch(IllegalStateException e){
        }

        // After runegistering, observer can't be notified.
        cursorWrapper.unregisterDataSetObserver(observer);
        observer.resetStatus();
        assertFalse(observer.hasInvalidated());
        cursorWrapper.moveToLast();
        cursorWrapper.deactivate();
        assertFalse(observer.hasInvalidated());

        // one DataSetObserver can't be unregistered twice continuously.
        try{
            cursorWrapper.unregisterDataSetObserver(observer);
            fail("testUnregisterDataSetObserver failed");
        }catch(IllegalStateException e){
        }
    }

    public void testGettingColumnInfos() {
        CursorWrapper cursorWrapper = new CursorWrapper(getCursor());

        assertEquals(DEFAULT_COLUMN_COUNT, cursorWrapper.getColumnCount());

        // Test getColumnIndex
        assertEquals(0, cursorWrapper.getColumnIndex("_id"));
        assertEquals(1, cursorWrapper.getColumnIndex("number"));
        assertEquals(-1, cursorWrapper.getColumnIndex("NON_EXISTENCE"));

        // Test getColumnIndexOrThrow
        assertEquals(0, cursorWrapper.getColumnIndexOrThrow("_id"));
        assertEquals(1, cursorWrapper.getColumnIndexOrThrow("number"));
        try {
            cursorWrapper.getColumnIndexOrThrow("NON_EXISTENCE");
            fail("getColumnIndexOrThrow should throws IllegalArgumentException if the column"
                    + "does not exist");
        } catch (IllegalArgumentException e) {
        }

        assertEquals("_id", cursorWrapper.getColumnName(0));
        assertEquals("number", cursorWrapper.getColumnName(1));

        String[] columnNames = cursorWrapper.getColumnNames();
        assertEquals(DEFAULT_COLUMN_COUNT, cursorWrapper.getColumnCount());
        assertEquals("_id", columnNames[0]);
        assertEquals("number", columnNames[1]);
        cursorWrapper.close();
    }

    public void testPositioning() {
        CursorWrapper cursorWrapper = new CursorWrapper(getCursor());

        // There are totally 2 records.
        // At first, the cursor is at beginning position: -1
        // Test isBeforeFirst, getPosition, isFirst
        assertTrue(cursorWrapper.isBeforeFirst());
        assertEquals(-1, cursorWrapper.getPosition());
        assertFalse(cursorWrapper.isFirst());

        // Test moveToNext
        assertTrue(cursorWrapper.moveToNext());
        assertEquals(0, cursorWrapper.getPosition());
        assertTrue(cursorWrapper.isFirst());

        // Test isLast
        assertFalse(cursorWrapper.isLast());
        assertTrue(cursorWrapper.moveToNext());
        assertEquals(1, cursorWrapper.getPosition());
        assertTrue(cursorWrapper.isLast());

        // move to the end
        // Test isLast and isAfterLast
        assertFalse(cursorWrapper.moveToNext());
        assertFalse(cursorWrapper.isLast());
        assertTrue(cursorWrapper.isAfterLast());
        assertEquals(2, cursorWrapper.getPosition());

        // Test move(int)
        assertTrue(cursorWrapper.move(-1));
        assertEquals(1, cursorWrapper.getPosition());
        assertTrue(cursorWrapper.move(-1));
        assertEquals(0, cursorWrapper.getPosition());
        // While reach the edge, function will return false
        assertFalse(cursorWrapper.move(-1));
        assertEquals(-1, cursorWrapper.getPosition());
        assertTrue(cursorWrapper.move(2));
        assertEquals(1, cursorWrapper.getPosition());
        // While reach the edge, function will return false
        assertFalse(cursorWrapper.move(1));
        assertTrue(cursorWrapper.isAfterLast());

        // Test moveToPrevious()
        assertEquals(2, cursorWrapper.getPosition());
        assertTrue(cursorWrapper.moveToPrevious());
        assertEquals(1, cursorWrapper.getPosition());
        assertTrue(cursorWrapper.moveToPrevious());
        assertEquals(0, cursorWrapper.getPosition());
        // While reach the edge, function will return false
        assertFalse(cursorWrapper.moveToPrevious());
        assertEquals(-1, cursorWrapper.getPosition());
        assertTrue(cursorWrapper.isBeforeFirst());

        // Test moveToPosition
        // While reach the edge, function will return false
        assertFalse(cursorWrapper.moveToPosition(2));
        assertEquals(2, cursorWrapper.getPosition());
        // While reach the edge, function will return false
        assertFalse(cursorWrapper.moveToPosition(-1));
        assertEquals(-1, cursorWrapper.getPosition());
        assertTrue(cursorWrapper.moveToPosition(1));
        assertEquals(1, cursorWrapper.getPosition());
        assertTrue(cursorWrapper.moveToPosition(0));
        assertEquals(0, cursorWrapper.getPosition());

        // Test moveToFirst and moveToFirst
        assertFalse(cursorWrapper.isLast());
        assertTrue(cursorWrapper.moveToLast());
        assertEquals(1, cursorWrapper.getPosition());
        assertTrue(cursorWrapper.isLast());
        assertFalse(cursorWrapper.isFirst());
        assertTrue(cursorWrapper.moveToFirst());
        assertEquals(0, cursorWrapper.getPosition());
        assertTrue(cursorWrapper.isFirst());
        cursorWrapper.close();
    }

    public void testGettingValues() {
        final byte NUMBER_BLOB_UNIT = 99;
        final String STRING_TEXT = "Test String";
        final String STRING_TEXT2 = "Test String2";
        final double NUMBER_DOUBLE = Double.MAX_VALUE;
        final double NUMBER_FLOAT = (float) NUMBER_DOUBLE;
        final long NUMBER_LONG_INTEGER = 0xaabbccddffL;
        final long NUMBER_INTEGER = (int) NUMBER_LONG_INTEGER;
        final long NUMBER_SHORT = (short) NUMBER_INTEGER;

        assertTrue(NUMBER_DOUBLE != NUMBER_FLOAT);
        assertTrue(NUMBER_LONG_INTEGER != NUMBER_INTEGER);
        assertTrue(NUMBER_LONG_INTEGER != (short) NUMBER_SHORT);
        assertTrue(NUMBER_INTEGER != (int) NUMBER_SHORT);

        // create table
        mDatabase.execSQL("CREATE TABLE test2 (_id INTEGER PRIMARY KEY, string_text TEXT,"
                + "double_number REAL, int_number INTEGER, blob_data BLOB);");
        // insert blob and other values
        Object[] args = new Object[4];
        byte[] originalBlob = new byte[1000];
        Arrays.fill(originalBlob, NUMBER_BLOB_UNIT);
        args[0] = STRING_TEXT;
        args[1] = NUMBER_DOUBLE;
        args[2] = NUMBER_LONG_INTEGER;
        args[3] = originalBlob;

        // Insert record.
        String sql = "INSERT INTO test2 (string_text, double_number, int_number, blob_data)"
                + "VALUES (?,?,?,?)";
        mDatabase.execSQL(sql, args);
        // use cursor to access blob
        Cursor cursor = mDatabase.query("test2", null, null, null, null, null, null);

        // Test getColumnCount
        CursorWrapper cursorWrapper = new CursorWrapper(getCursor());
        assertEquals(DEFAULT_COLUMN_COUNT, cursorWrapper.getColumnCount());
        cursorWrapper.close();
        cursorWrapper = new CursorWrapper(cursor);
        assertEquals(5, cursorWrapper.getColumnCount());

        cursorWrapper.moveToNext();
        int columnBlob = cursorWrapper.getColumnIndexOrThrow("blob_data");
        int columnString = cursorWrapper.getColumnIndexOrThrow("string_text");
        int columnDouble = cursorWrapper.getColumnIndexOrThrow("double_number");
        int columnInteger = cursorWrapper.getColumnIndexOrThrow("int_number");

        // Test getting value methods.
        byte[] targetBlob = cursorWrapper.getBlob(columnBlob);
        assertTrue(Arrays.equals(originalBlob, targetBlob));

        assertEquals(STRING_TEXT, cursorWrapper.getString(columnString));

        assertEquals(NUMBER_DOUBLE, cursorWrapper.getDouble(columnDouble), 0.000000000001);

        assertEquals(NUMBER_FLOAT, cursorWrapper.getFloat(columnDouble), 0.000000000001f);

        assertEquals(NUMBER_LONG_INTEGER, cursorWrapper.getLong(columnInteger));

        assertEquals(NUMBER_INTEGER, cursorWrapper.getInt(columnInteger));

        assertEquals(NUMBER_SHORT, cursorWrapper.getShort(columnInteger));

        // Test isNull(int).
        assertFalse(cursorWrapper.isNull(columnBlob));
        sql = "INSERT INTO test2 (string_text) VALUES ('" + STRING_TEXT2 + "')";
        mDatabase.execSQL(sql);
        cursorWrapper.close();
        cursor = mDatabase.query("test2", null, null, null, null, null, null);
        cursorWrapper = new CursorWrapper(cursor);
        cursorWrapper.moveToPosition(1);
        assertTrue(cursorWrapper.isNull(columnBlob));

        mDatabase.execSQL("DROP TABLE test2");
    }

    public void testGetExtras() {
        CursorWrapper cursor = new CursorWrapper(getCursor());
        Bundle bundle = cursor.getExtras();
        assertSame(Bundle.EMPTY, bundle);
    }

    public void testCopyStringToBuffer() {
        CharArrayBuffer charArrayBuffer = new CharArrayBuffer(1000);
        Cursor cursor = getCursor();

        CursorWrapper cursorWrapper = new CursorWrapper(cursor);
        cursorWrapper.moveToFirst();

        assertEquals(0, charArrayBuffer.sizeCopied);
        cursorWrapper.copyStringToBuffer(0, charArrayBuffer);
        String string = new String(charArrayBuffer.data);
        assertTrue(charArrayBuffer.sizeCopied > 0);
        assertEquals("1", string.substring(0, charArrayBuffer.sizeCopied));

        cursorWrapper.copyStringToBuffer(1, charArrayBuffer);
        string = new String(charArrayBuffer.data);
        assertTrue(charArrayBuffer.sizeCopied > 0);
        assertEquals(FIRST_NUMBER, string.substring(0, charArrayBuffer.sizeCopied));

        cursorWrapper.moveToNext();
        cursorWrapper.copyStringToBuffer(1, charArrayBuffer);
        string = new String(charArrayBuffer.data);
        assertTrue(charArrayBuffer.sizeCopied > 0);
        assertEquals(SECOND_NUMBER, string.substring(0, charArrayBuffer.sizeCopied));
        cursorWrapper.close();
    }

    public void testRespond() {
        Bundle b = new Bundle();
        CursorWrapper cursorWrapper = new CursorWrapper(getCursor());
        Bundle bundle = cursorWrapper.respond(b);
        assertSame(Bundle.EMPTY, bundle);
        cursorWrapper.close();
    }

    public void testGetWantsAllOnMoveCalls() {
        CursorWrapper cursorWrapper = new CursorWrapper(getCursor());
        assertFalse(cursorWrapper.getWantsAllOnMoveCalls());
        cursorWrapper.close();
    }

    public void testContentObserverOperations() throws IllegalStateException {
        CursorWrapper cursorWrapper = new CursorWrapper(getCursor());
        MockContentObserver observer = new MockContentObserver(null);

        // Can't unregister a Observer before it has been registered.
        try{
            cursorWrapper.unregisterContentObserver(observer);
            fail("testUnregisterContentObserver failed");
        }catch(IllegalStateException e){
            assertTrue(true);
        }

        cursorWrapper.registerContentObserver(observer);

        // Can't register a same observer twice before unregister it.
        try{
            cursorWrapper.registerContentObserver(observer);
            fail("testRegisterContentObserver failed");
        }catch(IllegalStateException e){
        }

        cursorWrapper.unregisterContentObserver(observer);
        // one Observer can be registered again after it has been unregistered.
        cursorWrapper.registerContentObserver(observer);

        cursorWrapper.unregisterContentObserver(observer);

        try{
            cursorWrapper.unregisterContentObserver(observer);
            fail("testUnregisterContentObserver failed");
        }catch(IllegalStateException e){
        }
        cursorWrapper.close();
    }

    public void testSetExtras() {
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M){
            Cursor cursor = getCursor();
            CursorWrapper cursorWrapper = new CursorWrapper(cursor);
            try {
                Bundle b = new Bundle();
                cursorWrapper.setExtras(b);
                assertSame(b, cursor.getExtras());
            } finally {
                cursorWrapper.close();
            }
        }
    }

    private class MockContentObserver extends ContentObserver {

        public MockContentObserver(Handler handler) {
            super(handler);
        }
    }

    private void deleteWithValue(SQLiteDatabase database, int value) {
        database.execSQL("DELETE FROM test1 WHERE number = " + value + ";");
    }

    private void addWithValue(SQLiteDatabase database, int value) {
        database.execSQL("INSERT INTO test1 (number) VALUES ('" + value + "');");
    }

    private void deleteAllRecords(SQLiteDatabase database) {
        database.delete("test1", null, null);
    }

    private void setupDatabase() {
        File dbDir = getContext().getDir("tests", Context.MODE_PRIVATE);
        /* don't use the same database name as the one in super class
         * this class's setUp() method deletes a database file just opened by super.setUp().
         * that can cause corruption in database in the following situation:
         *    super.setUp() creates the database, inserts some data into it.
         *    this class setUp() deletes just the database file but not the associated
         *    database files such as wal, shm files.
         * solution is to have this class delete the whole database directory.
         * better yet, this class shouldn't extend DatabaseCursortest at all.
         * TODO: fix this bogus cts class hierarchy
         */
        mDatabaseFile = new File(dbDir, "cursor_test.db");
        if (mDatabaseFile.exists()) {
            mDatabaseFile.delete();
        }
        mDatabase = SQLiteDatabase.openOrCreateDatabase(mDatabaseFile.getPath(), null);
        assertNotNull(mDatabase);
        mDatabase.setVersion(CURRENT_DATABASE_VERSION);
        mDatabase.execSQL("CREATE TABLE test1 (_id INTEGER PRIMARY KEY, number TEXT);");
        mDatabase.execSQL("INSERT INTO test1 (number) VALUES ('" + FIRST_NUMBER + "');");
        mDatabase.execSQL("INSERT INTO test1 (number) VALUES ('" + SECOND_NUMBER + "');");
        mCursor = getCursor();
    }

    private void closeDatabase() {
        if (null != mCursor) {
            mCursor.close();
            mCursor = null;
        }
        mDatabase.close();
        mDatabaseFile.delete();
    }

    private void rebuildDatabase() {
        closeDatabase();
        setupDatabase();
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
}
