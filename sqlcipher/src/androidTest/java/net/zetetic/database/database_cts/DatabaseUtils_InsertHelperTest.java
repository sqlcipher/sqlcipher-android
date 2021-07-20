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

package net.zetetic.database.database_cts;


import android.content.ContentValues;
import android.database.Cursor;
import net.zetetic.database.DatabaseUtils.InsertHelper;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

import java.io.File;

public class DatabaseUtils_InsertHelperTest extends AndroidTestCase {
    private static final String TEST_TABLE_NAME = "test";
    private static final String DATABASE_NAME = "database_test.db";

    private SQLiteDatabase mDatabase;
    private InsertHelper mInsertHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        System.loadLibrary("sqlcipher");
        getContext().deleteDatabase(DATABASE_NAME);
        File f = mContext.getDatabasePath(DATABASE_NAME);
        mDatabase = SQLiteDatabase.openOrCreateDatabase(f, null);
        assertNotNull(mDatabase);
        mInsertHelper = new InsertHelper(mDatabase, TEST_TABLE_NAME);
    }

    @Override
    protected void tearDown() throws Exception {
        mInsertHelper.close();
        mDatabase.close();
        getContext().deleteDatabase(DATABASE_NAME);
        super.tearDown();
    }

    public void testConstructor() {
        new InsertHelper(mDatabase, TEST_TABLE_NAME);
    }

    public void testClose() {
        mInsertHelper.close();
    }

    public void testGetColumnIndex() {
        mDatabase.execSQL("CREATE TABLE " + TEST_TABLE_NAME + " (_id INTEGER PRIMARY KEY, " +
                "name TEXT, age INTEGER, address TEXT);");
        assertEquals(1, mInsertHelper.getColumnIndex("_id"));
        assertEquals(2, mInsertHelper.getColumnIndex("name"));
        assertEquals(3, mInsertHelper.getColumnIndex("age"));
        assertEquals(4, mInsertHelper.getColumnIndex("address"));
        try {
            mInsertHelper.getColumnIndex("missing_column");
            fail("Should throw exception (column does not exist)");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testInsert() {
        mDatabase.execSQL("CREATE TABLE " + TEST_TABLE_NAME + "(_id INTEGER PRIMARY KEY," +
                " boolean_value INTEGER, int_value INTEGER, long_value INTEGER," +
                " double_value DOUBLE, float_value DOUBLE, string_value TEXT," +
                " blob_value BLOB, null_value TEXT);");
        final int booleanValueIndex = 1;
        final int intValueIndex     = 2;
        final int longValueIndex    = 3;
        final int doubleValueIndex  = 4;
        final int floatValueIndex   = 5;
        final int stringValueIndex  = 6;
        final int blobValueIndex    = 7;
        final int nullValueIndex    = 8;
        final String[] projection = new String[] {
            "_id",                    // index 0
            "boolean_value",          // index 1
            "int_value",              // index 2
            "long_value",             // index 3
            "double_value",           // index 4
            "float_value",            // index 5
            "string_value",           // index 6
            "blob_value",             // index 7
            "null_value"              // index 8
        };

        Cursor cursor = mDatabase.query(TEST_TABLE_NAME, projection, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());
        cursor.close();

        try {
            mInsertHelper.execute();
            fail("Should throw exception (execute without prepare)");
        } catch (IllegalStateException expected) {
            // expected
        }

        mInsertHelper.prepareForInsert();
        mInsertHelper.bind(mInsertHelper.getColumnIndex("boolean_value"), true);
        mInsertHelper.bind(mInsertHelper.getColumnIndex("int_value"), 10);
        mInsertHelper.bind(mInsertHelper.getColumnIndex("long_value"), 1000L);
        mInsertHelper.bind(mInsertHelper.getColumnIndex("double_value"), 123.456);
        mInsertHelper.bind(mInsertHelper.getColumnIndex("float_value"), 1.0f);
        mInsertHelper.bind(mInsertHelper.getColumnIndex("string_value"), "test insert");
        byte[] blob = new byte[] { '1', '2', '3' };
        mInsertHelper.bind(mInsertHelper.getColumnIndex("blob_value"), blob);
        mInsertHelper.bindNull(mInsertHelper.getColumnIndex("null_value"));
        long id = mInsertHelper.execute();
        assertEquals(1, id);

        cursor = mDatabase.query(TEST_TABLE_NAME, projection, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals(1, cursor.getInt(booleanValueIndex));
        assertEquals(10, cursor.getInt(intValueIndex));
        assertEquals(1000L, cursor.getLong(longValueIndex));
        assertEquals(123.456, cursor.getDouble(doubleValueIndex));
        assertEquals(1.0f, cursor.getFloat(floatValueIndex));
        assertEquals("test insert", cursor.getString(stringValueIndex));
        byte[] value = cursor.getBlob(blobValueIndex);
        MoreAsserts.assertEquals(blob, value);
        assertNull(cursor.getString(nullValueIndex));
        cursor.close();

        // try inserting a conflicting column -> should return -1
        mInsertHelper.prepareForInsert();
        mInsertHelper.bind(mInsertHelper.getColumnIndex("_id"), id);
        assertEquals(-1, mInsertHelper.execute());

        // subsequent insert() should ignore existing bindings
        ContentValues values = new ContentValues();
        values.put("boolean_value", false);
        values.put("int_value", 123);
        values.put("long_value", 987654L);
        values.put("double_value", 654.321);
        values.put("float_value", 21.1f);
        values.put("string_value", "insert another row");
        values.put("blob_value", blob);
        values.putNull("null_value");
        id = mInsertHelper.insert(values);
        assertEquals(2, id);
        cursor = mDatabase.query(TEST_TABLE_NAME, projection, "_id = " + id,
                null, null, null, null);
        assertNotNull(cursor);
        cursor.moveToFirst();
        assertEquals(0, cursor.getInt(booleanValueIndex));
        assertEquals(123, cursor.getInt(intValueIndex));
        assertEquals(987654L, cursor.getLong(longValueIndex));
        assertEquals(654.321, cursor.getDouble(doubleValueIndex));
        assertEquals(21.1f, cursor.getFloat(floatValueIndex));
        assertEquals("insert another row", cursor.getString(stringValueIndex));
        value = cursor.getBlob(blobValueIndex);
        MoreAsserts.assertEquals(blob, value);
        assertNull(cursor.getString(nullValueIndex));
        cursor.close();

        // try inserting a conflicting column -> should return -1
        values.put("_id", id);
        assertEquals(-1, mInsertHelper.insert(values));
    }

    public void testReplace() {
        mDatabase.execSQL("CREATE TABLE " + TEST_TABLE_NAME + "(_id INTEGER PRIMARY KEY," +
                " boolean_value INTEGER, int_value INTEGER, long_value INTEGER," +
                " double_value DOUBLE, float_value DOUBLE, string_value TEXT," +
                " blob_value BLOB, null_value TEXT);");
        final int booleanValueIndex = 1;
        final int intValueIndex     = 2;
        final int longValueIndex    = 3;
        final int doubleValueIndex  = 4;
        final int floatValueIndex   = 5;
        final int stringValueIndex  = 6;
        final int blobValueIndex    = 7;
        final int nullValueIndex    = 8;
        final String[] projection = new String[] {
            "_id",                    // index 0
            "boolean_value",          // index 1
            "int_value",              // index 2
            "long_value",             // index 3
            "double_value",           // index 4
            "float_value",            // index 5
            "string_value",           // index 6
            "blob_value",             // index 7
            "null_value"              // index 8
        };

        Cursor cursor = mDatabase.query(TEST_TABLE_NAME, projection, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());
        cursor.close();

        // without specifying a key, this becomes an insert
        mInsertHelper.prepareForReplace();
        mInsertHelper.bind(mInsertHelper.getColumnIndex("boolean_value"), true);
        mInsertHelper.bind(mInsertHelper.getColumnIndex("int_value"), 10);
        mInsertHelper.bind(mInsertHelper.getColumnIndex("long_value"), 1000L);
        mInsertHelper.bind(mInsertHelper.getColumnIndex("double_value"), 123.456);
        mInsertHelper.bind(mInsertHelper.getColumnIndex("float_value"), 1.0f);
        mInsertHelper.bind(mInsertHelper.getColumnIndex("string_value"), "test insert");
        byte[] blob = new byte[] { '1', '2', '3' };
        mInsertHelper.bind(mInsertHelper.getColumnIndex("blob_value"), blob);
        mInsertHelper.bindNull(mInsertHelper.getColumnIndex("null_value"));
        long id = mInsertHelper.execute();
        assertEquals(1, id);

        cursor = mDatabase.query(TEST_TABLE_NAME, projection, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals(1, cursor.getInt(booleanValueIndex));
        assertEquals(10, cursor.getInt(intValueIndex));
        assertEquals(1000L, cursor.getLong(longValueIndex));
        assertEquals(123.456, cursor.getDouble(doubleValueIndex));
        assertEquals(1.0f, cursor.getFloat(floatValueIndex));
        assertEquals("test insert", cursor.getString(stringValueIndex));
        byte[] value = cursor.getBlob(blobValueIndex);
        MoreAsserts.assertEquals(blob, value);
        assertNull(cursor.getString(nullValueIndex));
        cursor.close();

        mInsertHelper.prepareForReplace();
        mInsertHelper.bind(mInsertHelper.getColumnIndex("_id"), id);
        mInsertHelper.bind(mInsertHelper.getColumnIndex("int_value"), 42);
        mInsertHelper.execute();
        cursor = mDatabase.query(TEST_TABLE_NAME, projection, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals(42, cursor.getInt(intValueIndex));
        // previous bindings are forgotten
        assertNull(cursor.getString(stringValueIndex));
        cursor.close();

        // illegal primary key -> should return -1
        mInsertHelper.prepareForReplace();
        mInsertHelper.bind(mInsertHelper.getColumnIndex("_id"), "illegal_id");
        assertEquals(-1, mInsertHelper.execute());

        ContentValues values = new ContentValues();
        // will replace row id
        values.put("_id", id);
        values.put("boolean_value", false);
        values.put("int_value", 123);
        values.put("long_value", 987654L);
        values.put("double_value", 654.321);
        values.put("float_value", 21.1f);
        values.put("string_value", "replace the row");
        values.put("blob_value", blob);
        values.putNull("null_value");
        id = mInsertHelper.replace(values);
        assertEquals(1, id);
        cursor = mDatabase.query(TEST_TABLE_NAME, projection, null, null, null, null, null);
        assertEquals(1, cursor.getCount());
        assertNotNull(cursor);
        cursor.moveToFirst();
        assertEquals(0, cursor.getInt(booleanValueIndex));
        assertEquals(123, cursor.getInt(intValueIndex));
        assertEquals(987654L, cursor.getLong(longValueIndex));
        assertEquals(654.321, cursor.getDouble(doubleValueIndex));
        assertEquals(21.1f, cursor.getFloat(floatValueIndex));
        assertEquals("replace the row", cursor.getString(stringValueIndex));
        value = cursor.getBlob(blobValueIndex);
        MoreAsserts.assertEquals(blob, value);
        assertNull(cursor.getString(nullValueIndex));
        cursor.close();

        // illegal primary key -> should return -1
        values.put("_id", "illegal_id");
        assertEquals(-1, mInsertHelper.replace(values));
    }
}
