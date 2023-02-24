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


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteException;
import android.os.ParcelFileDescriptor;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

import net.zetetic.database.DatabaseUtils;
import net.zetetic.database.DatabaseUtils.InsertHelper;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteStatement;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

public class DatabaseUtilsTest extends AndroidTestCase {
    private SQLiteDatabase mDatabase;
    private File mDatabaseFile;
    private static final String[] TEST_PROJECTION = new String[] {
        "_id",             // 0
        "name",            // 1
        "age",             // 2
        "address"          // 3
    };
    private static final String TABLE_NAME = "test";

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
        mDatabase.execSQL("CREATE TABLE " + TABLE_NAME + " (_id INTEGER PRIMARY KEY, " +
                "name TEXT, age INTEGER, address TEXT);");
        mDatabase.execSQL(
                "CREATE TABLE blob_test (_id INTEGER PRIMARY KEY, name TEXT, data BLOB)");
        mDatabase.execSQL(
                "CREATE TABLE boolean_test (_id INTEGER PRIMARY KEY, value BOOLEAN)");
    }

    @Override
    protected void tearDown() throws Exception {
        mDatabase.close();
        mDatabaseFile.delete();
        super.tearDown();
    }

    public void testAppendEscapedSQLString() {
        String expected = "name='Mike'";
        StringBuilder sb = new StringBuilder("name=");
        DatabaseUtils.appendEscapedSQLString(sb, "Mike");
        assertEquals(expected, sb.toString());

        expected = "'name=''Mike'''";
        sb = new StringBuilder();
        DatabaseUtils.appendEscapedSQLString(sb, "name='Mike'");
        assertEquals(expected, sb.toString());
    }

    public void testSqlEscapeString() {
        String expected = "'Jack'";
        assertEquals(expected, DatabaseUtils.sqlEscapeString("Jack"));
    }

    public void testAppendValueToSql() {
        String expected = "address='LA'";
        StringBuilder sb = new StringBuilder("address=");
        DatabaseUtils.appendValueToSql(sb, "LA");
        assertEquals(expected, sb.toString());

        expected = "address=NULL";
        sb = new StringBuilder("address=");
        DatabaseUtils.appendValueToSql(sb, null);
        assertEquals(expected, sb.toString());

        expected = "flag=1";
        sb = new StringBuilder("flag=");
        DatabaseUtils.appendValueToSql(sb, true);
        assertEquals(expected, sb.toString());
    }

    public void testBindObjectToProgram() {
        String name = "Mike";
        int age = 21;
        String address = "LA";

        // at the beginning, there are no records in the database.
        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION,
                null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());

        String sql = "INSERT INTO " + TABLE_NAME + " (name, age, address) VALUES (?, ?, ?);";
        SQLiteStatement statement = mDatabase.compileStatement(sql);
        DatabaseUtils.bindObjectToProgram(statement, 1, name);
        DatabaseUtils.bindObjectToProgram(statement, 2, age);
        DatabaseUtils.bindObjectToProgram(statement, 3, address);
        statement.execute();
        statement.close();

        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals(name, cursor.getString(1));
        assertEquals(age, cursor.getInt(2));
        assertEquals(address, cursor.getString(3));
    }

    public void testCreateDbFromSqlStatements() {
        String dbName = "ExampleName";
        String sqls = "CREATE TABLE " + TABLE_NAME + " (_id INTEGER PRIMARY KEY, name TEXT);\n"
                + "INSERT INTO " + TABLE_NAME + " (name) VALUES ('Mike');\n";
        File f = mContext.getDatabasePath(dbName);
        f.mkdirs();
        f.delete();
        DatabaseUtils.createDbFromSqlStatements(getContext(), f.toString(), 1, sqls);
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(f,null);

        final String[] PROJECTION = new String[] {
            "_id",             // 0
            "name"             // 1
        };
        Cursor cursor = db.query(TABLE_NAME, PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(1));
        getContext().deleteDatabase(dbName);
    }

    public void testCursorDoubleToContentValues() {
        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Mike', '20', 'LA');");
        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION,
                null, null, null, null, null);
        assertNotNull(cursor);

        ContentValues contentValues = new ContentValues();
        String key = "key";
        cursor.moveToFirst();
        DatabaseUtils.cursorDoubleToContentValues(cursor, "age", contentValues, key);
        assertEquals(20.0, contentValues.getAsDouble(key));

        DatabaseUtils.cursorDoubleToContentValues(cursor, "Error Field Name", contentValues, key);
        assertNull(contentValues.getAsDouble(key));

        DatabaseUtils.cursorDoubleToContentValues(cursor, "name", contentValues, key);
        assertEquals(0.0, contentValues.getAsDouble(key));
    }

    public void testCursorDoubleToCursorValues() {
        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Mike', '20', 'LA');");
        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION,
                null, null, null, null, null);
        assertNotNull(cursor);

        ContentValues contentValues = new ContentValues();
        cursor.moveToFirst();
        DatabaseUtils.cursorDoubleToCursorValues(cursor, "age", contentValues);
        assertEquals(20.0, contentValues.getAsDouble("age"));

        DatabaseUtils.cursorDoubleToCursorValues(cursor, "Error Field Name", contentValues);
        assertNull(contentValues.getAsDouble("Error Field Name"));

        DatabaseUtils.cursorDoubleToCursorValues(cursor, "name", contentValues);
        assertEquals(0.0, contentValues.getAsDouble("name"));
    }

    public void testCursorIntToContentValues() {
        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Mike', '20', 'LA');");
        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);

        ContentValues contentValues = new ContentValues();
        String key = "key";
        cursor.moveToFirst();
        DatabaseUtils.cursorIntToContentValues(cursor, "age", contentValues, key);
        assertEquals(Integer.valueOf(20), contentValues.getAsInteger(key));

        DatabaseUtils.cursorIntToContentValues(cursor, "Error Field Name", contentValues, key);
        assertNull(contentValues.getAsInteger(key));

        DatabaseUtils.cursorIntToContentValues(cursor, "name", contentValues, key);
        assertEquals(Integer.valueOf(0), contentValues.getAsInteger(key));

        contentValues = new ContentValues();
        DatabaseUtils.cursorIntToContentValues(cursor, "age", contentValues);
        assertEquals(Integer.valueOf(20), contentValues.getAsInteger("age"));

        DatabaseUtils.cursorIntToContentValues(cursor, "Error Field Name", contentValues);
        assertNull(contentValues.getAsInteger("Error Field Name"));

        DatabaseUtils.cursorIntToContentValues(cursor, "name", contentValues);
        assertEquals(Integer.valueOf(0), contentValues.getAsInteger("name"));
    }

    public void testcursorLongToContentValues() {
        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Mike', '20', 'LA');");
        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);

        ContentValues contentValues = new ContentValues();
        String key = "key";
        cursor.moveToNext();
        DatabaseUtils.cursorLongToContentValues(cursor, "age", contentValues, key);
        assertEquals(Long.valueOf(20), contentValues.getAsLong(key));

        DatabaseUtils.cursorLongToContentValues(cursor, "Error Field Name", contentValues, key);
        assertNull(contentValues.getAsLong(key));

        DatabaseUtils.cursorLongToContentValues(cursor, "name", contentValues, key);
        assertEquals(Long.valueOf(0), contentValues.getAsLong(key));

        contentValues = new ContentValues();
        DatabaseUtils.cursorLongToContentValues(cursor, "age", contentValues);
        assertEquals(Long.valueOf(20), contentValues.getAsLong("age"));

        DatabaseUtils.cursorLongToContentValues(cursor, "Error Field Name", contentValues);
        assertNull(contentValues.getAsLong("Error Field Name"));

        DatabaseUtils.cursorLongToContentValues(cursor, "name", contentValues);
        assertEquals(Long.valueOf(0), contentValues.getAsLong("name"));
    }

    public void testCursorRowToContentValues() {
        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Mike', '20', 'LA');");
        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION,
                null, null, null, null, null);
        assertNotNull(cursor);

        ContentValues contentValues = new ContentValues();
        cursor.moveToNext();
        DatabaseUtils.cursorRowToContentValues(cursor, contentValues);
        assertEquals("Mike", (String) contentValues.get("name"));
        assertEquals("20", (String) contentValues.get("age"));
        assertEquals("LA", (String) contentValues.get("address"));

        mDatabase.execSQL("INSERT INTO boolean_test (value)" +
                " VALUES (0);");
        mDatabase.execSQL("INSERT INTO boolean_test (value)" +
                " VALUES (1);");
        cursor = mDatabase.query("boolean_test", new String[] {"value"},
                null, null, null, null, null);
        assertNotNull(cursor);

        contentValues = new ContentValues();
        cursor.moveToNext();
        DatabaseUtils.cursorRowToContentValues(cursor, contentValues);
        assertFalse(contentValues.getAsBoolean("value"));
        assertTrue(contentValues.getAsInteger("value")==0);
        cursor.moveToNext();
        DatabaseUtils.cursorRowToContentValues(cursor, contentValues);
        assertTrue(contentValues.getAsInteger("value")==1);

        // The following does not work:
        // https://stackoverflow.com/questions/13095277/android-database-with-contentvalue-with-boolean-bug
        // assertTrue(contentValues.getAsBoolean("value"));
    }

    public void testCursorStringToContentValues() {
        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Mike', '20', 'LA');");
        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION,
                null, null, null, null, null);
        assertNotNull(cursor);

        ContentValues contentValues = new ContentValues();
        String key = "key";
        cursor.moveToNext();
        DatabaseUtils.cursorStringToContentValues(cursor, "age", contentValues, key);
        assertEquals("20", (String) contentValues.get(key));

        try {
            DatabaseUtils.cursorStringToContentValues(cursor, "Error Field Name",
                    contentValues, key);
            fail("should throw IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // expected
        }

        DatabaseUtils.cursorStringToContentValues(cursor, "name", contentValues, key);
        assertEquals("Mike", contentValues.get(key));

        contentValues = new ContentValues();
        DatabaseUtils.cursorStringToContentValues(cursor, "age", contentValues);
        assertEquals("20", contentValues.get("age"));

        try {
            DatabaseUtils.cursorStringToContentValues(cursor, "Error Field Name", contentValues);
            fail("should throw IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // expected
        }

        DatabaseUtils.cursorStringToContentValues(cursor, "name", contentValues);
        assertEquals("Mike", contentValues.get("name"));
    }

    public void testCursorStringToInsertHelper() {
        // create a new table.
        mDatabase.execSQL("CREATE TABLE test_copy (_id INTEGER PRIMARY KEY, " +
                "name TEXT, age INTEGER, address TEXT);");

        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Mike', '20', 'LA');");
        Cursor cursor = mDatabase.query("test_copy", TEST_PROJECTION, null, null, null, null, null);
        assertEquals(0, cursor.getCount());

        InsertHelper insertHelper = new InsertHelper(mDatabase, "test_copy");
        int indexName = insertHelper.getColumnIndex("name");
        int indexAge = insertHelper.getColumnIndex("age");
        int indexAddress = insertHelper.getColumnIndex("address");

        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null, null, null);
        cursor.moveToNext();
        insertHelper.prepareForInsert();
        DatabaseUtils.cursorStringToInsertHelper(cursor, "name", insertHelper, indexName);
        DatabaseUtils.cursorStringToInsertHelper(cursor, "age", insertHelper, indexAge);
        DatabaseUtils.cursorStringToInsertHelper(cursor, "address", insertHelper, indexAddress);
        insertHelper.execute();

        cursor = mDatabase.query("test_copy", TEST_PROJECTION, null, null, null, null, null);
        assertEquals(1, cursor.getCount());
        cursor.moveToNext();
        assertEquals("Mike", cursor.getString(1));
        assertEquals(20, cursor.getInt(2));
        assertEquals("LA", cursor.getString(3));
    }

    public void testDumpCurrentRow() {
        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Mike', '20', 'LA');");
        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION,
                null, null, null, null, null);
        assertNotNull(cursor);
        cursor.moveToNext();
        String expected = "0 {\n   _id=1\n   name=Mike\n   age=20\n   address=LA\n}\n";

        DatabaseUtils.dumpCurrentRow(cursor);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream os = new PrintStream(bos);
        DatabaseUtils.dumpCurrentRow(cursor, os);
        os.flush();
        os.close();
        assertEquals(expected, bos.toString());

        StringBuilder sb = new StringBuilder();
        DatabaseUtils.dumpCurrentRow(cursor, sb);
        assertEquals(expected, sb.toString());

        assertEquals(expected, DatabaseUtils.dumpCurrentRowToString(cursor));
    }

    public void testDumpCursor() {
        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Mike', '20', 'LA');");
        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Jack', '30', 'London');");
        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION,
                null, null, null, null, null);
        assertNotNull(cursor);
        int pos = cursor.getPosition();
        String expected = ">>>>> Dumping cursor " + cursor + "\n" +
                "0 {\n" +
                "   _id=1\n" +
                "   name=Mike\n" +
                "   age=20\n" +
                "   address=LA\n" +
                "}\n" +
                "1 {\n" +
                "   _id=2\n" +
                "   name=Jack\n" +
                "   age=30\n" +
                "   address=London\n" +
                "}\n" +
                "<<<<<\n";

        DatabaseUtils.dumpCursor(cursor);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream os = new PrintStream(bos);
        DatabaseUtils.dumpCursor(cursor, os);
        os.flush();
        os.close();
        assertEquals(pos, cursor.getPosition()); // dumpCursor should not change status of cursor
        assertEquals(expected, bos.toString());

        StringBuilder sb = new StringBuilder();
        DatabaseUtils.dumpCursor(cursor, sb);
        assertEquals(pos, cursor.getPosition()); // dumpCursor should not change status of cursor
        assertEquals(expected, sb.toString());

        assertEquals(expected, DatabaseUtils.dumpCursorToString(cursor));
        assertEquals(pos, cursor.getPosition()); // dumpCursor should not change status of cursor
    }

    public void testCollationKey() {
        String key1 = DatabaseUtils.getCollationKey("abc");
        String key2 = DatabaseUtils.getCollationKey("ABC");
        String key3 = DatabaseUtils.getCollationKey("bcd");

        assertTrue(key1.equals(key2));
        assertFalse(key1.equals(key3));

        key1 = DatabaseUtils.getHexCollationKey("abc");
        key2 = DatabaseUtils.getHexCollationKey("ABC");
        key3 = DatabaseUtils.getHexCollationKey("bcd");

        assertTrue(key1.equals(key2));
        assertFalse(key1.equals(key3));
    }

    public void testLongForQuery() {
        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Mike', '20', 'LA');");

        String query = "SELECT age FROM " + TABLE_NAME;
        assertEquals(20, DatabaseUtils.longForQuery(mDatabase, query, null));

        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Jack', '35', 'London');");
        query = "SELECT age FROM " + TABLE_NAME + " WHERE name = ?";
        String[] args = new String[] { "Jack" };
        assertEquals(35, DatabaseUtils.longForQuery(mDatabase, query, args));
        args = new String[] { "No such name" };
        try {
            DatabaseUtils.longForQuery(mDatabase, query, args);
            fail("should throw SQLiteDoneException");
        } catch (SQLiteDoneException e) {
            // expected
        }

        query = "SELECT count(*) FROM " + TABLE_NAME + ";";
        SQLiteStatement statement = mDatabase.compileStatement(query);
        assertEquals(2, DatabaseUtils.longForQuery(statement, null));

        query = "SELECT age FROM " + TABLE_NAME + " WHERE address = ?;";
        statement = mDatabase.compileStatement(query);
        args = new String[] { "London" };
        assertEquals(35, DatabaseUtils.longForQuery(statement, args));

        args = new String[] { "No such address" };
        try {
            DatabaseUtils.longForQuery(statement, args);
            fail("should throw SQLiteDoneException");
        } catch (SQLiteDoneException e) {
            // expected
        }
        statement.close();
    }

    public void testQueryNumEntries() {
        assertEquals(0, DatabaseUtils.queryNumEntries(mDatabase, TABLE_NAME));

        mDatabase.execSQL(
                "INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Mike', '20', 'LA');");
        assertEquals(1, DatabaseUtils.queryNumEntries(mDatabase, TABLE_NAME));

        mDatabase.execSQL(
                "INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Susan', '20', 'AR');");
        assertEquals(2, DatabaseUtils.queryNumEntries(mDatabase, TABLE_NAME));

        mDatabase.execSQL(
                "INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Christian', '25', 'AT');");
        assertEquals(3, DatabaseUtils.queryNumEntries(mDatabase, TABLE_NAME));

        assertEquals(2, DatabaseUtils.queryNumEntries(mDatabase, TABLE_NAME, "AGE = 20"));

        assertEquals(1, DatabaseUtils.queryNumEntries(mDatabase, TABLE_NAME, "AGE = ?",
                new String[] { "25" }));

        try {
            DatabaseUtils.queryNumEntries(mDatabase, "NoSuchTable");
            fail("should throw SQLiteException.");
        } catch (SQLiteException e) {
            // expected
        }
    }

//    public void testExceptionFromParcel() {
//        Parcel parcel = Parcel.obtain();
//        DatabaseUtils.writeExceptionToParcel(parcel, new IllegalArgumentException());
//        parcel.setDataPosition(0);
//        try {
//            DatabaseUtils.readExceptionFromParcel(parcel);
//            fail("should throw IllegalArgumentException.");
//        } catch (IllegalArgumentException e) {
//            // expected
//        }
//
//        parcel = Parcel.obtain();
//        DatabaseUtils.writeExceptionToParcel(parcel, new SQLiteAbortException());
//        parcel.setDataPosition(0);
//        try {
//            DatabaseUtils.readExceptionFromParcel(parcel);
//            fail("should throw SQLiteAbortException.");
//        } catch (SQLiteAbortException e) {
//            // expected
//        }
//
//        parcel = Parcel.obtain();
//        DatabaseUtils.writeExceptionToParcel(parcel, new FileNotFoundException());
//        parcel.setDataPosition(0);
//        try {
//            DatabaseUtils.readExceptionFromParcel(parcel);
//            fail("should throw RuntimeException.");
//        } catch (RuntimeException e) {
//            // expected
//        }
//
//        parcel = Parcel.obtain();
//        DatabaseUtils.writeExceptionToParcel(parcel, new FileNotFoundException());
//        parcel.setDataPosition(0);
//        try {
//            DatabaseUtils.readExceptionWithFileNotFoundExceptionFromParcel(parcel);
//            fail("should throw FileNotFoundException.");
//        } catch (FileNotFoundException e) {
//            // expected
//        }
//    }

    public void testStringForQuery() {
        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Mike', '20', 'LA');");

        String query = "SELECT name FROM " + TABLE_NAME;
        assertEquals("Mike", DatabaseUtils.stringForQuery(mDatabase, query, null));

        mDatabase.execSQL("INSERT INTO " + TABLE_NAME + " (name, age, address)" +
                " VALUES ('Jack', '35', 'London');");
        query = "SELECT name FROM " + TABLE_NAME + " WHERE address = ?";
        String[] args = new String[] { "London" };
        assertEquals("Jack", DatabaseUtils.stringForQuery(mDatabase, query, args));
        args = new String[] { "No such address" };
        try {
            DatabaseUtils.stringForQuery(mDatabase, query, args);
            fail("should throw SQLiteDoneException");
        } catch (SQLiteDoneException e) {
            // expected
        }

        query = "SELECT name FROM " + TABLE_NAME + " WHERE age = ?;";
        SQLiteStatement statement = mDatabase.compileStatement(query);
        args = new String[] { "20" };
        assertEquals("Mike", DatabaseUtils.stringForQuery(statement, args));

        args = new String[] { "1000" }; // NO people can be older than this.
        try {
            DatabaseUtils.blobFileDescriptorForQuery(statement, args);
            fail("should throw SQLiteDoneException");
        } catch (SQLiteDoneException e) {
            // expected
        }
        statement.close();
    }

//    public void testBlobFileDescriptorForQuery() throws Exception {
//        String data1 = "5300FEFF";
//        String data2 = "DECAFBAD";
//        mDatabase.execSQL("INSERT INTO blob_test (name, data) VALUES ('Mike', X'" + data1 + "')");
//
//        String query = "SELECT data FROM blob_test";
//        assertFileDescriptorContent(parseBlob(data1),
//                        DatabaseUtils.blobFileDescriptorForQuery(mDatabase, query, null));
//
//        mDatabase.execSQL("INSERT INTO blob_test (name, data) VALUES ('Jack', X'" + data2 + "');");
//        query = "SELECT data FROM blob_test WHERE name = ?";
//        String[] args = new String[] { "Jack" };
//        assertFileDescriptorContent(parseBlob(data2),
//                DatabaseUtils.blobFileDescriptorForQuery(mDatabase, query, args));
//
//        args = new String[] { "No such name" };
//        try {
//            DatabaseUtils.stringForQuery(mDatabase, query, args);
//            fail("should throw SQLiteDoneException");
//        } catch (SQLiteDoneException e) {
//            // expected
//        }
//
//        query = "SELECT data FROM blob_test WHERE name = ?;";
//        SQLiteStatement statement = mDatabase.compileStatement(query);
//        args = new String[] { "Mike" };
//        assertFileDescriptorContent(parseBlob(data1),
//                DatabaseUtils.blobFileDescriptorForQuery(statement, args));
//
//        args = new String[] { "No such name" };
//        try {
//            DatabaseUtils.blobFileDescriptorForQuery(statement, args);
//            fail("should throw SQLiteDoneException");
//        } catch (SQLiteDoneException e) {
//            // expected
//        }
//        statement.close();
//    }

    private static byte[] parseBlob(String src) {
        int len = src.length();
        byte[] result = new byte[len / 2];

        for (int i = 0; i < len/2; i++) {
            int val;
            char c1 = src.charAt(i*2);
            char c2 = src.charAt(i*2+1);
            int val1 = Character.digit(c1, 16);
            int val2 = Character.digit(c2, 16);
            val = (val1 << 4) | val2;
            result[i] = (byte)val;
        }
        return result;
    }

    private static void assertFileDescriptorContent(byte[] expected, ParcelFileDescriptor fd)
            throws IOException {
        assertInputStreamContent(expected, new ParcelFileDescriptor.AutoCloseInputStream(fd));
    }

    private static void assertInputStreamContent(byte[] expected, InputStream is)
            throws IOException {
        try {
            byte[] observed = new byte[expected.length];
            int count = is.read(observed);
            assertEquals(expected.length, count);
            assertEquals(-1, is.read());
            MoreAsserts.assertEquals(expected, observed);
        } finally {
            is.close();
        }
    }

}
