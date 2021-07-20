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
import android.database.Cursor;
import android.database.CursorJoiner;
import android.database.CursorJoiner.Result;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import android.test.AndroidTestCase;

import java.io.File;

public class CursorJoinerTest extends AndroidTestCase {

    private static final int TEST_ITEM_COUNT = 10;
    private static final int DEFAULT_TABLE1_VALUE_BEGINS = 1;
    private static final int DEFAULT_TABLE2_VALUE_BEGINS = 11;
    private static final int EQUAL_START = 18;
    // Every table has 7 unique numbers, and 3 other numbers they all have.
    private static final int UNIQUE_COUNT = 7;
    private static final int MAX_VALUE = 20;
    private static final int EQUAL_VALUE_COUNT = MAX_VALUE - EQUAL_START + 1;
    private static final String TABLE_NAME_1 = "test1";
    private static final String TABLE_NAME_2 = "test2";
    private static final String TABLE1_COLUMNS = " number TEXT";
    private static final String TABLE2_COLUMNS = " number TEXT, int_number INTEGER";

    private SQLiteDatabase mDatabase;
    private File mDatabaseFile;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        System.loadLibrary("sqlcipher");
        setupDatabase();
    }

    @Override
    protected void tearDown() throws Exception {
        mDatabase.close();
        mDatabaseFile.delete();
        super.tearDown();
    }

    public void testCursorJoinerAndIterator() {
        Cursor cursor1 = getCursor(TABLE_NAME_1, null, null);
        Cursor cursor2 = getCursor(TABLE_NAME_2, null, null);
        // Test with different length ColumenNAmes
        try {
            new CursorJoiner(cursor1, cursor1.getColumnNames(), cursor2, cursor2.getColumnNames());
            fail("CursorJoiner's constructor should throws  IllegalArgumentException here.");
        } catch (IllegalArgumentException e) {
            //expected
        }
        closeCursor(cursor1);
        closeCursor(cursor2);

        String[] columnNames = new String[] { "number" };
        cursor1 = getCursor(TABLE_NAME_1, null, columnNames);
        cursor2 = getCursor(TABLE_NAME_2, null, columnNames);

        CursorJoiner cursorJoiner = new CursorJoiner(cursor1, cursor1.getColumnNames(), cursor2,
                cursor2.getColumnNames());

        // Test remove()
        try {
            cursorJoiner.remove();
            fail("remove() should throws UnsupportedOperationException here");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        assertEquals(TEST_ITEM_COUNT, cursor1.getCount());
        assertEquals(TEST_ITEM_COUNT, cursor2.getCount());

        // Test iterator
        for (CursorJoiner.Result joinResult : cursorJoiner) {
            switch (joinResult) {
            case LEFT:
                // Add the values into table test1 which table test1 possess and table test2 don't.
                assertTrue(cursor1.getString(0).compareTo(cursor2.getString(0)) < 0);
                addValueIntoTable(TABLE_NAME_2, cursor1.getString(0));
                break;
            case RIGHT:
                // Add the values into table test2 which table test2 possess and table test1 don't.
                assertTrue(cursor1.getString(0).compareTo(cursor2.getString(0)) > 0);
                addValueIntoTable(TABLE_NAME_1, cursor2.getString(0));
                break;
            case BOTH:
                // Delete the values table test1 and test2 both possess.
                assertEquals(cursor1.getString(0), cursor2.getString(0));
                deleteValueFromTable(TABLE_NAME_1, cursor1.getString(0));
                deleteValueFromTable(TABLE_NAME_2, cursor2.getString(0));
                break;
            }
        }
        cursor1.requery();
        cursor2.requery();

        // Finally, two tables's number columns have the same contents
        assertEquals(UNIQUE_COUNT * 2, cursor1.getCount());
        assertEquals(UNIQUE_COUNT * 2, cursor2.getCount());

        // For every  table, merged with the other one's unique numbers, and deleted the originally
        // mutual same numbers(EQUAL_START~MAX_VALUE);
        cursor1.moveToFirst();
        cursor2.moveToFirst();
        for (int i = 0; i < UNIQUE_COUNT; i++) {
            assertEquals(getOrderNumberString(DEFAULT_TABLE1_VALUE_BEGINS + i, MAX_VALUE),
                    cursor1.getString(0));
            assertEquals(cursor1.getString(0), cursor2.getString(0));
            cursor1.moveToNext();
            cursor2.moveToNext();
        }
        closeCursor(cursor2);
        closeCursor(cursor1);
    }

    public void testNext() {
        String[] columnNames = new String[] { "number" };
        Cursor cursor1 = getCursor(TABLE_NAME_1, null, columnNames);
        Cursor cursor2 = getCursor(TABLE_NAME_2, null, columnNames);

        // For cursor1 , values are '01'~'07' and 'EQUAL_START'~'MAX_VALUE'
        assertEquals(TEST_ITEM_COUNT, cursor1.getCount());
        // For cursor2 , values are '11'~'17' and 'EQUAL_START'~'MAX_VALUE'
        assertEquals(TEST_ITEM_COUNT, cursor2.getCount());
        CursorJoiner cursorJoiner = new CursorJoiner(cursor1, cursor1.getColumnNames(), cursor2,
                cursor2.getColumnNames());
        for (int i = 0; i < UNIQUE_COUNT; i++) {
            // For cursor1, value 1~7 result value as LEFT to cursor2 value '11'
            assertTrue(cursorJoiner.hasNext());
            assertEquals(Result.LEFT, cursorJoiner.next());
            assertEquals(getOrderNumberString(DEFAULT_TABLE1_VALUE_BEGINS + i, MAX_VALUE), cursor1
                    .getString(0));
            assertEquals(getOrderNumberString(DEFAULT_TABLE2_VALUE_BEGINS, MAX_VALUE), cursor2
                  .getString(0));
        }
        for (int i = 0; i < UNIQUE_COUNT; i++) {
            // For cursor2, value 11~17 result a value as LEFT to cursor1 value '18'
            assertTrue(cursorJoiner.hasNext());
            assertEquals(Result.RIGHT, cursorJoiner.next());
            assertEquals(getOrderNumberString(EQUAL_START, MAX_VALUE), cursor1.getString(0));
            assertEquals(getOrderNumberString(DEFAULT_TABLE2_VALUE_BEGINS + i, MAX_VALUE), cursor2
                    .getString(0));
        }
        for (int i = 0; i < EQUAL_VALUE_COUNT; i++) {
            // For cursor1 and cursor2, value 18~20 result a value as BOTH
            assertTrue(cursorJoiner.hasNext());
            assertEquals(Result.BOTH, cursorJoiner.next());
            assertEquals(getOrderNumberString(EQUAL_START + i, MAX_VALUE), cursor1.getString(0));
            assertEquals(getOrderNumberString(EQUAL_START + i, MAX_VALUE), cursor2.getString(0));
        }
        closeCursor(cursor1);
        closeCursor(cursor2);
    }

    /**
     * This function accepts integer maxValue to determine max length of number.
     * Return a converted decimal number string of input integer parameter 'value',
     *  according to  the max length, '0' will be placeholder(s).
     * For example: if max length is 2, 1 -> '01', 10 -> '10'.
     * @param value
     * @param maxValue
     * @return
     */
    private String getOrderNumberString(int value, int maxValue) {
        // Convert decimal number as string, '0' as placeholder
        int maxLength = Integer.toString(maxValue).length();
        int basicLength = Integer.toString(value).length();
        String placeHolders = "";
        for (int i = 0; i < (maxLength - basicLength); i++) {
            placeHolders += "0";
        }
        return placeHolders + Integer.toString(value);
    }

    private void initializeTables() {
        // Add 1 to 7 into Table1
        addValuesIntoTable(TABLE_NAME_1, DEFAULT_TABLE1_VALUE_BEGINS,
                DEFAULT_TABLE1_VALUE_BEGINS + UNIQUE_COUNT - 1);
        // Add 18 to 20 into Table1
        addValuesIntoTable(TABLE_NAME_1, DEFAULT_TABLE2_VALUE_BEGINS + UNIQUE_COUNT, MAX_VALUE);
        // Add 11 to 17 into Table2
        addValuesIntoTable(TABLE_NAME_2, DEFAULT_TABLE2_VALUE_BEGINS,
                DEFAULT_TABLE2_VALUE_BEGINS + UNIQUE_COUNT - 1);
        // Add 18 to 20 into Table2
        addValuesIntoTable(TABLE_NAME_2, DEFAULT_TABLE2_VALUE_BEGINS + UNIQUE_COUNT, MAX_VALUE);
    }

    private void setupDatabase() {
        File dbDir = getContext().getDir("tests", Context.MODE_PRIVATE);
        mDatabaseFile = new File(dbDir, "database_test.db");
        if (mDatabaseFile.exists()) {
            mDatabaseFile.delete();
        }
        mDatabase = SQLiteDatabase.openOrCreateDatabase(mDatabaseFile.getPath(), null);
        assertNotNull(mDatabaseFile);
        createTable(TABLE_NAME_1, TABLE1_COLUMNS);
        createTable(TABLE_NAME_2, TABLE2_COLUMNS);
        initializeTables();
    }

    private void closeCursor(Cursor cursor) {
        if (null != cursor) {
            cursor.close();
            cursor = null;
        }
    }

    private void createTable(String tableName, String columnNames) {
        String sql = "Create TABLE " + tableName + " (_id INTEGER PRIMARY KEY, " + columnNames
                + " );";
        mDatabase.execSQL(sql);
    }

    private void addValuesIntoTable(String tableName, int start, int end) {
        for (int i = start; i <= end; i++) {
            mDatabase.execSQL("INSERT INTO " + tableName + "(number) VALUES ('"
                    + getOrderNumberString(i, MAX_VALUE) + "');");
        }
    }

    private void addValueIntoTable(String tableName, String value) {
        mDatabase.execSQL("INSERT INTO " + tableName + "(number) VALUES ('" + value + "');");
    }

    private void deleteValueFromTable(String tableName, String value) {
        mDatabase.execSQL("DELETE FROM " + tableName + " WHERE number = '" + value + "';");
    }

    private Cursor getCursor(String tableName, String selection, String[] columnNames) {
        return mDatabase.query(tableName, columnNames, selection, null, null, null, "number");
    }
}
