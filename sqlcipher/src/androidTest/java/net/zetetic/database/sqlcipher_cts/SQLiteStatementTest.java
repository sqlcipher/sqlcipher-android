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


import android.content.ContentValues;
import android.database.Cursor;
import net.zetetic.database.DatabaseUtils;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteStatement;

import android.database.SQLException;
import android.database.sqlite.SQLiteDoneException;
import android.os.ParcelFileDescriptor;
import androidx.test.filters.Suppress;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;

public class SQLiteStatementTest extends AndroidTestCase {
    private static final String STRING1 = "this is a test";
    private static final String STRING2 = "another test";

    private static final byte[][] BLOBS = new byte [][] {
        parseBlob("86FADCF1A820666AEBD0789F47932151A2EF734269E8AC4E39630AB60519DFD8"),
        new byte[1],
        null,
        parseBlob("00"),
        parseBlob("FF"),
        parseBlob("D7B500FECF25F7A4D83BF823D3858690790F2526013DE6CAE9A69170E2A1E47238"),
    };

    private static final String DATABASE_NAME = "database_test.db";

    private static final int CURRENT_DATABASE_VERSION = 42;
    private SQLiteDatabase mDatabase;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        System.loadLibrary("sqlcipher");
        getContext().deleteDatabase(DATABASE_NAME);
        File f = mContext.getDatabasePath(DATABASE_NAME);
        f.mkdirs();
        if (f.exists()) { f.delete(); }
        mDatabase = SQLiteDatabase.openOrCreateDatabase(f, null);
        assertNotNull(mDatabase);
        mDatabase.setVersion(CURRENT_DATABASE_VERSION);
    }

    @Override
    protected void tearDown() throws Exception {
        mDatabase.close();
        getContext().deleteDatabase(DATABASE_NAME);
        super.tearDown();
    }

    private void populateDefaultTable() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, data TEXT);");
    }

    private void populateBlobTable() {
        mDatabase.execSQL("CREATE TABLE blob_test (_id INTEGER PRIMARY KEY, data BLOB)");
        for (int i = 0; i < BLOBS.length; i++) {
            ContentValues values = new ContentValues();
            values.put("_id", i);
            values.put("data", BLOBS[i]);
            mDatabase.insert("blob_test", null, values);
        }
    }

    public void testExecute() {
        mDatabase.disableWriteAheadLogging();
        populateDefaultTable();

        assertEquals(0, DatabaseUtils.longForQuery(mDatabase, "select count(*) from test", null));

        // test update
        // insert 2 rows and then update them.
        SQLiteStatement statement1 = mDatabase.compileStatement(
                "INSERT INTO test (data) VALUES ('" + STRING2 + "')");
        assertEquals(1, statement1.executeInsert());
        assertEquals(2, statement1.executeInsert());
        SQLiteStatement statement2 =
                mDatabase.compileStatement("UPDATE test set data = 'a' WHERE _id > 0");
        assertEquals(2, statement2.executeUpdateDelete());
        statement2.close();
        // should still have 2 rows in the table
        assertEquals(2, DatabaseUtils.longForQuery(mDatabase, "select count(*) from test", null));

        // test delete
        // insert 2 more rows and delete 3 of them
        assertEquals(3, statement1.executeInsert());
        assertEquals(4, statement1.executeInsert());
        statement1.close();
        statement2 = mDatabase.compileStatement("DELETE from test WHERE _id < 4");
        assertEquals(3, statement2.executeUpdateDelete());
        statement2.close();
        // should still have 1 row1 in the table
        assertEquals(1, DatabaseUtils.longForQuery(mDatabase, "select count(*) from test", null));

        // if the SQL statement is something that causes rows of data to
        // be returned, executeUpdateDelete() (and execute()) throw an exception.
        statement2 = mDatabase.compileStatement("SELECT count(*) FROM test");
        try {
            statement2.executeUpdateDelete();
            fail("exception expected");
        } catch (SQLException e) {
            // expected
        } finally {
            statement2.close();
        }
    }

    public void testExecuteInsert() {
        populateDefaultTable();

        Cursor c = mDatabase.query("test", null, null, null, null, null, null);
        assertEquals(0, c.getCount());

        // test insert
        SQLiteStatement statement = mDatabase.compileStatement(
                "INSERT INTO test (data) VALUES ('" + STRING2 + "')");
        assertEquals(1, statement.executeInsert());
        statement.close();

        // try to insert another row with the same id. last inserted rowid should be -1
        statement = mDatabase.compileStatement("insert or ignore into test values(1, 1);");
        assertEquals(-1, statement.executeInsert());
        statement.close();

        c = mDatabase.query("test", null, null, null, null, null, null);
        assertEquals(1, c.getCount());

        c.moveToFirst();
        assertEquals(STRING2, c.getString(c.getColumnIndex("data")));
        c.close();

        // if the sql statement is something that causes rows of data to
        // be returned, executeInsert() throws an exception
        statement = mDatabase.compileStatement(
                "SELECT * FROM test WHERE data=\"" + STRING2 + "\"");
        try {
            statement.executeInsert();
            fail("exception expected");
        } catch (SQLException e) {
            // expected
        } finally {
            statement.close();

        }
    }

    public void testSimpleQueryForLong() {
        mDatabase.execSQL("CREATE TABLE test (num INTEGER NOT NULL, str TEXT NOT NULL);");
        mDatabase.execSQL("INSERT INTO test VALUES (1234, 'hello');");
        SQLiteStatement statement =
                mDatabase.compileStatement("SELECT num FROM test WHERE str = ?");

        // test query long
        statement.bindString(1, "hello");
        long value = statement.simpleQueryForLong();
        assertEquals(1234, value);

        // test query returns zero rows
        statement.bindString(1, "world");

        try {
            statement.simpleQueryForLong();
            fail("There should be a SQLiteDoneException thrown out.");
        } catch (SQLiteDoneException e) {
            // expected.
        }

        statement.close();
    }

    public void testSimpleQueryForString() {
        mDatabase.execSQL("CREATE TABLE test (num INTEGER NOT NULL, str TEXT NOT NULL);");
        mDatabase.execSQL("INSERT INTO test VALUES (1234, 'hello');");
        SQLiteStatement statement =
                mDatabase.compileStatement("SELECT str FROM test WHERE num = ?");

        // test query String
        statement.bindLong(1, 1234);
        String value = statement.simpleQueryForString();
        assertEquals("hello", value);

        // test query returns zero rows
        statement.bindLong(1, 5678);

        try {
            statement.simpleQueryForString();
            fail("There should be a SQLiteDoneException thrown out.");
        } catch (SQLiteDoneException e) {
            // expected.
        }

        statement.close();
    }

    @Suppress
    public void testSimpleQueryForBlobFileDescriptorSuccessNormal() throws IOException {
        doTestSimpleQueryForBlobFileDescriptorSuccess(0);
    }

    @Suppress
    public void testSimpleQueryForBlobFileDescriptorSuccessEmpty() throws IOException {
        doTestSimpleQueryForBlobFileDescriptorSuccess(1);
    }

    @Suppress
    public void testSimpleQueryForBlobFileDescriptorSuccessNull() {
        populateBlobTable();

        String sql = "SELECT data FROM blob_test WHERE _id = " + 2;
        SQLiteStatement stm = mDatabase.compileStatement(sql);
        assertNull(stm.simpleQueryForBlobFileDescriptor());
    }

    @Suppress
    public void testSimpleQueryForBlobFileDescriptorSuccess00() throws IOException {
        doTestSimpleQueryForBlobFileDescriptorSuccess(3);
    }

    @Suppress
    public void testSimpleQueryForBlobFileDescriptorSuccessFF() throws IOException {
        doTestSimpleQueryForBlobFileDescriptorSuccess(4);
    }

    @Suppress
    public void testSimpleQueryForBlobFileDescriptorSuccessEmbeddedNul() throws IOException {
        doTestSimpleQueryForBlobFileDescriptorSuccess(5);
    }

    @Suppress
    private void doTestSimpleQueryForBlobFileDescriptorSuccess(int i) throws IOException {
        populateBlobTable();

        String sql = "SELECT data FROM blob_test WHERE _id = " + i;
        SQLiteStatement stm = mDatabase.compileStatement(sql);
        ParcelFileDescriptor fd = stm.simpleQueryForBlobFileDescriptor();
        assertFileDescriptorContent(BLOBS[i], fd);
    }

    @Suppress
    public void testSimpleQueryForBlobFileDescriptorSuccessParam() throws IOException {
        populateBlobTable();

        String sql = "SELECT data FROM blob_test WHERE _id = ?";
        SQLiteStatement stm = mDatabase.compileStatement(sql);
        stm.bindLong(1, 0);
        ParcelFileDescriptor fd = stm.simpleQueryForBlobFileDescriptor();
        assertFileDescriptorContent(BLOBS[0], fd);
    }

    public void testGetBlobFailureNoParam() throws Exception {
        populateBlobTable();

        String sql = "SELECT data FROM blob_test WHERE _id = 100";
        SQLiteStatement stm = mDatabase.compileStatement(sql);
        ParcelFileDescriptor fd = null;
        SQLiteDoneException expectedException = null;
        try {
            fd = stm.simpleQueryForBlobFileDescriptor();
        } catch (SQLiteDoneException ex) {
            expectedException = ex;
        } finally {
            if (fd != null) {
                fd.close();
                fd = null;
            }
        }
        assertNotNull("Should have thrown SQLiteDoneException", expectedException);
    }

    public void testGetBlobFailureParam() throws Exception {
        populateBlobTable();

        String sql = "SELECT data FROM blob_test WHERE _id = ?";
        SQLiteStatement stm = mDatabase.compileStatement(sql);
        stm.bindLong(1, 100);
        ParcelFileDescriptor fd = null;
        SQLiteDoneException expectedException = null;
        try {
            fd = stm.simpleQueryForBlobFileDescriptor();
        } catch (SQLiteDoneException ex) {
            expectedException = ex;
        } finally {
            if (fd != null) {
                fd.close();
                fd = null;
            }
        }
        assertNotNull("Should have thrown SQLiteDoneException", expectedException);
    }

    /*
     * Convert string of hex digits to byte array.
     * Results are undefined for poorly formed string.
     *
     * @param src hex string
     */
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
