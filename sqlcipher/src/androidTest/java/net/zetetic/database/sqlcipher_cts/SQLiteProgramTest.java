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


import android.database.Cursor;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteException;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteStatement;

import java.io.File;

public class SQLiteProgramTest extends AndroidTestCase {
    private static final String DATABASE_NAME = "database_test.db";

    private SQLiteDatabase mDatabase;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        System.loadLibrary("sqlcipher");
        File f = mContext.getDatabasePath(DATABASE_NAME);
        f.mkdirs();
        if (f.exists()) { f.delete(); }
        mDatabase = SQLiteDatabase.openOrCreateDatabase(f,null);
        assertNotNull(mDatabase);
    }

    @Override
    protected void tearDown() throws Exception {
        mDatabase.close();
        getContext().deleteDatabase(DATABASE_NAME);

        super.tearDown();
    }

    public void testBind() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, text1 TEXT, text2 TEXT, " +
                "num1 INTEGER, num2 INTEGER, image BLOB);");
        mDatabase.execSQL("INSERT INTO test (text1, text2, num1, num2, image) " +
                "VALUES ('Mike', 'Jack', 12, 30, 'abcdefg');");
        mDatabase.execSQL("INSERT INTO test (text1, text2, num1, num2, image) " +
                "VALUES ('test1', 'test2', 213, 589, '123456789');");
        SQLiteStatement statement;

        statement = mDatabase.compileStatement("SELECT num1 FROM test WHERE num2 = ?;");
        statement.bindLong(1, 30);
        assertEquals(12, statement.simpleQueryForLong());

        // re-bind without clearing
        statement.bindDouble(1, 589.0);
        assertEquals(213, statement.simpleQueryForLong());
        statement.close();

        statement = mDatabase.compileStatement("SELECT text1 FROM test WHERE text2 = ?;");

        statement.bindDouble(1, 589.0); // Wrong binding
        try {
            statement.simpleQueryForString();
            fail("Should throw exception (no rows found)");
        } catch (SQLiteDoneException expected) {
            // expected
        }
        statement.bindString(1, "test2");
        assertEquals("test1", statement.simpleQueryForString());
        statement.clearBindings();
        try {
            statement.simpleQueryForString();
            fail("Should throw exception (unbound value)");
        } catch (SQLiteDoneException expected) {
            // expected
        }
        statement.close();

        Cursor cursor = null;
        try {
            cursor = mDatabase.query("test", new String[]{"text1"}, "where text1='a'",
                    new String[]{"foo"}, null, null, null);
            fail("Should throw exception (no value to bind)");
        } catch (SQLiteException expected) {
            // expected
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        try {
            cursor = mDatabase.query("test", new String[]{"text1"}, "where text1='a'",
                    new String[]{"foo", "bar"}, null, null, null);
            fail("Should throw exception (index too large)");
        } catch (SQLiteException expected) {
            // expected
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        // test positive case
        statement = mDatabase.compileStatement(
                "SELECT text1 FROM test WHERE text2 = ? AND num2 = ?;");
        statement.bindString(1, "Jack");
        statement.bindLong(2, 30);
        assertEquals("Mike", statement.simpleQueryForString());
        statement.close();
    }

    public void testBindNull() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, text1 TEXT, text2 TEXT, " +
                "num1 INTEGER, num2 INTEGER, image BLOB);");

        SQLiteStatement statement = mDatabase.compileStatement("INSERT INTO test " +
                "(text1,text2,num1,image) VALUES (?,?,?,?)");
        statement.bindString(1, "string1");
        statement.bindString(2, "string2");
        statement.bindLong(3, 100);
        statement.bindNull(4);
        statement.execute();
        statement.close();

        final int COLUMN_TEXT1_INDEX = 0;
        final int COLUMN_TEXT2_INDEX = 1;
        final int COLUMN_NUM1_INDEX = 2;
        final int COLUMN_IMAGE_INDEX = 3;

        Cursor cursor = mDatabase.query("test", new String[] { "text1", "text2", "num1", "image" },
                null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("string1", cursor.getString(COLUMN_TEXT1_INDEX));
        assertEquals("string2", cursor.getString(COLUMN_TEXT2_INDEX));
        assertEquals(100, cursor.getInt(COLUMN_NUM1_INDEX));
        assertNull(cursor.getString(COLUMN_IMAGE_INDEX));
        cursor.close();
    }

    public void testBindBlob() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, text1 TEXT, text2 TEXT, " +
                "num1 INTEGER, num2 INTEGER, image BLOB);");

        SQLiteStatement statement = mDatabase.compileStatement("INSERT INTO test " +
                "(text1,text2,num1,image) VALUES (?,?,?,?)");
        statement.bindString(1, "string1");
        statement.bindString(2, "string2");
        statement.bindLong(3, 100);
        byte[] blob = new byte[] { '1', '2', '3' };
        statement.bindBlob(4, blob);
        statement.execute();
        statement.close();

        final int COLUMN_TEXT1_INDEX = 0;
        final int COLUMN_TEXT2_INDEX = 1;
        final int COLUMN_NUM1_INDEX = 2;
        final int COLUMN_IMAGE_INDEX = 3;

        Cursor cursor = mDatabase.query("test", new String[] { "text1", "text2", "num1", "image" },
                null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("string1", cursor.getString(COLUMN_TEXT1_INDEX));
        assertEquals("string2", cursor.getString(COLUMN_TEXT2_INDEX));
        assertEquals(100, cursor.getInt(COLUMN_NUM1_INDEX));
        byte[] value = cursor.getBlob(COLUMN_IMAGE_INDEX);
        MoreAsserts.assertEquals(blob, value);
        cursor.close();
    }
}
