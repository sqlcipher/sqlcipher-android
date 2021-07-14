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

import android.database.CharArrayBuffer;
import android.database.CursorWindow;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteException;
import android.os.Parcel;
import android.test.AndroidTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class CursorWindowTest extends AndroidTestCase {

    private static final String TEST_STRING = "Test String";

    public void testWriteCursorToWindow() throws Exception {
        // create cursor
        String[] colNames = new String[]{"_id", "name", "number", "profit"};
        int colsize = colNames.length;
        ArrayList<ArrayList<Integer>> list = createTestList(10, colsize);
        MatrixCursor cursor = new MatrixCursor(colNames, list.size());
        for (ArrayList<Integer> row : list) {
            cursor.addRow(row);
        }

        // fill window
        CursorWindow window = new CursorWindow(false);
        cursor.fillWindow(0, window);

        // read from cursor window
        for (int i = 0; i < list.size(); i++) {
            ArrayList<Integer> col = list.get(i);
            for (int j = 0; j < colsize; j++) {
                String s = window.getString(i, j);
                int r2 = col.get(j);
                int r1 = Integer.parseInt(s);
                assertEquals(r2, r1);
            }
        }

        // test cursor window handle startpos != 0
        window.clear();
        cursor.fillWindow(1, window);
        // read from cursor from window
        for (int i = 1; i < list.size(); i++) {
            ArrayList<Integer> col = list.get(i);
            for (int j = 0; j < colsize; j++) {
                String s = window.getString(i, j);
                int r2 = col.get(j);
                int r1 = Integer.parseInt(s);
                assertEquals(r2, r1);
            }
        }

        // Clear the window and make sure it's empty
        window.clear();
        assertEquals(0, window.getNumRows());
    }

    public void testNull() {
        CursorWindow window = getOneByOneWindow();

        // Put in a null value and read it back as various types
        assertTrue(window.putNull(0, 0));
        assertNull(window.getString(0, 0));
        assertEquals(0, window.getLong(0, 0));
        assertEquals(0.0, window.getDouble(0, 0));
        assertNull(window.getBlob(0, 0));
    }

    public void testEmptyString() {
        CursorWindow window = getOneByOneWindow();

        // put size 0 string and read it back as various types
        assertTrue(window.putString("", 0, 0));
        assertEquals("", window.getString(0, 0));
        assertEquals(0, window.getLong(0, 0));
        assertEquals(0.0, window.getDouble(0, 0));
    }

    public void testConstructors() {
        int TEST_NUMBER = 5;
        CursorWindow cursorWindow;

        // Test constructor with 'true' input, and getStartPosition should return 0
        cursorWindow = new CursorWindow(true);
        assertEquals(0, cursorWindow.getStartPosition());

        // Test constructor with 'false' input
        cursorWindow = new CursorWindow(false);
        assertEquals(0, cursorWindow.getStartPosition());

        // Test newFromParcel
        Parcel parcel = Parcel.obtain();
        cursorWindow = new CursorWindow(true);
        cursorWindow.setStartPosition(TEST_NUMBER);
        cursorWindow.setNumColumns(1);
        cursorWindow.allocRow();
        cursorWindow.putString(TEST_STRING, TEST_NUMBER, 0);
        cursorWindow.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        cursorWindow = CursorWindow.CREATOR.createFromParcel(parcel);
        assertEquals(TEST_NUMBER, cursorWindow.getStartPosition());
        assertEquals(TEST_STRING, cursorWindow.getString(TEST_NUMBER, 0));
    }

    public void testDataStructureOperations() {
        CursorWindow cursorWindow = new CursorWindow(true);

        // Test with normal values
        assertTrue(cursorWindow.setNumColumns(0));
        // If the column has been set to zero, can't put String.
        assertFalse(cursorWindow.putString(TEST_STRING, 0, 0));

        // Test allocRow().
        assertTrue(cursorWindow.allocRow());
        assertEquals(1, cursorWindow.getNumRows());
        assertTrue(cursorWindow.allocRow());
        assertEquals(2, cursorWindow.getNumRows());
        // Though allocate a row, but the column number is still 0, so can't putString.
        assertFalse(cursorWindow.putString(TEST_STRING, 0, 0));

        // Test freeLstRow
        cursorWindow.freeLastRow();
        assertEquals(1, cursorWindow.getNumRows());
        cursorWindow.freeLastRow();
        assertEquals(0, cursorWindow.getNumRows());

        cursorWindow = new CursorWindow(true);
        assertTrue(cursorWindow.setNumColumns(6));
        assertTrue(cursorWindow.allocRow());
        // Column number set to negative number, so now can put values.
        assertTrue(cursorWindow.putString(TEST_STRING, 0, 0));
        assertEquals(TEST_STRING, cursorWindow.getString(0, 0));

        // Test with negative value
        assertFalse(cursorWindow.setNumColumns(-1));

        // Test with reference limitation
        cursorWindow.releaseReference();
        try {
            cursorWindow.setNumColumns(5);
            fail("setNumColumns() should throws IllegalStateException here.");
        } catch (IllegalStateException e) {
            // expected
        }

        // Test close(), close will also minus references, that will lead acquireReference()
        // related operation failed.
        cursorWindow.close();
        try {
            cursorWindow.acquireReference();
            fail("setNumColumns() should throws IllegalStateException here.");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testAccessDataValues() {
        final long NUMBER_LONG_INTEGER = (long) 0xaabbccddffL;
        final long NUMBER_INTEGER = (int) NUMBER_LONG_INTEGER;
        final long NUMBER_SHORT = (short) NUMBER_INTEGER;
        final float NUMBER_FLOAT_SCIENCE = 7.332952E11f;
        final double NUMBER_DOUBLE_SCIENCE = 7.33295205887E11;
        final String NUMBER_FLOAT_SCIENCE_STRING = "7.332952E11";
        final String NUMBER_DOUBLE_SCIENCE_STRING = "7.33295205887E11";
        final String NUMBER_FLOAT_SCIENCE_STRING2 = "7.33295e+11";

        byte[] originalBlob = new byte[Byte.MAX_VALUE];
        for (int i = 0; i < Byte.MAX_VALUE; i++) {
            originalBlob[i] = (byte) i;
        }

        CursorWindow cursorWindow = new CursorWindow(true);
        cursorWindow.setNumColumns(5);
        cursorWindow.allocRow();

        // Test putString, getString, getLong, getInt, isBlob
        assertTrue(cursorWindow.putString(Long.toString(NUMBER_LONG_INTEGER), 0, 0));
        assertEquals(Long.toString(NUMBER_LONG_INTEGER), cursorWindow.getString(0, 0));
        assertEquals(NUMBER_LONG_INTEGER, cursorWindow.getLong(0, 0));
        assertEquals(NUMBER_INTEGER, cursorWindow.getInt(0, 0));
        assertEquals(NUMBER_SHORT, cursorWindow.getShort(0, 0));
        // Converting of Float, there would be some little precision differences. So just compare
        // first 6 digits.
        assertEquals(NUMBER_FLOAT_SCIENCE_STRING.substring(0, 6), Float.toString(
                cursorWindow.getFloat(0, 0)).substring(0, 6));
        assertEquals(NUMBER_DOUBLE_SCIENCE_STRING, Double.toString(cursorWindow.getDouble(0, 0)));
        assertFalse(cursorWindow.isNull(0, 0));
        assertFalse(cursorWindow.isBlob(0, 0));

        // Test null String
        assertTrue(cursorWindow.putString("", 0, 0));
        assertEquals("", cursorWindow.getString(0, 0));
        assertEquals(0, cursorWindow.getLong(0, 0));
        assertEquals(0, cursorWindow.getInt(0, 0));
        assertEquals(0, cursorWindow.getShort(0, 0));
        assertEquals(0.0, cursorWindow.getDouble(0, 0));
        assertEquals(0.0f, cursorWindow.getFloat(0, 0), 0.00000001f);
        assertFalse(cursorWindow.isNull(0, 0));
        assertFalse(cursorWindow.isBlob(0, 0));

        // Test putNull, getString, getLong, getDouble, getBlob, getInd, getShort, getFloat,
        // isBlob.
        assertTrue(cursorWindow.putNull(0, 1));
        assertNull(cursorWindow.getString(0, 1));
        assertEquals(0, cursorWindow.getLong(0, 1));
        assertEquals(0, cursorWindow.getInt(0, 1));
        assertEquals(0, cursorWindow.getShort(0, 1));
        assertEquals(0.0, cursorWindow.getDouble(0, 1));
        assertEquals(0.0f, cursorWindow.getFloat(0, 1), 0.00000001f);
        assertNull(cursorWindow.getBlob(0, 1));
        assertTrue(cursorWindow.isNull(0, 1));
        // If the field is null, isBlob will return true.
        assertTrue(cursorWindow.isBlob(0, 1));

        // Test putLong, getLong, getInt, getString , getShort, getFloat, getDouble, isBlob.
        assertTrue(cursorWindow.putLong(NUMBER_LONG_INTEGER, 0, 2));
        assertEquals(NUMBER_LONG_INTEGER, cursorWindow.getLong(0, 2));
        assertEquals(NUMBER_INTEGER, cursorWindow.getInt(0, 2));
        assertEquals(Long.toString(NUMBER_LONG_INTEGER), cursorWindow.getString(0, 2));
        assertEquals(NUMBER_SHORT, cursorWindow.getShort(0, 2));
        assertEquals(NUMBER_FLOAT_SCIENCE, cursorWindow.getFloat(0, 2), 0.00000001f);
        assertEquals(NUMBER_DOUBLE_SCIENCE, cursorWindow.getDouble(0, 2), 0.00000001);
        try {
            cursorWindow.getBlob(0, 2);
            fail("Can't get Blob from a Integer value.");
        } catch (SQLiteException e) {
            // expected
        }
        assertFalse(cursorWindow.isNull(0, 2));
        assertFalse(cursorWindow.isBlob(0, 2));

        // Test putDouble
        assertTrue(cursorWindow.putDouble(NUMBER_DOUBLE_SCIENCE, 0, 3));
        assertEquals(NUMBER_LONG_INTEGER, cursorWindow.getLong(0, 3));
        assertEquals(NUMBER_INTEGER, cursorWindow.getInt(0, 3));
        // Converting from Double to String, there would be some little precision differences. So
        // Just compare first 6 digits.
        assertEquals(NUMBER_FLOAT_SCIENCE_STRING2.substring(0, 6), cursorWindow.getString(0, 3)
                .substring(0, 6));
        assertEquals(NUMBER_SHORT, cursorWindow.getShort(0, 3));
        assertEquals(NUMBER_FLOAT_SCIENCE, cursorWindow.getFloat(0, 3), 0.00000001f);
        assertEquals(NUMBER_DOUBLE_SCIENCE, cursorWindow.getDouble(0, 3), 0.00000001);
        try {
            cursorWindow.getBlob(0, 3);
            fail("Can't get Blob from a Double value.");
        } catch (SQLiteException e) {
            // expected
        }
        assertFalse(cursorWindow.isNull(0, 3));
        assertFalse(cursorWindow.isBlob(0, 3));

        // Test putBlob
        assertTrue(cursorWindow.putBlob(originalBlob, 0, 4));
        byte[] targetBlob = cursorWindow.getBlob(0, 4);
        assertTrue(Arrays.equals(originalBlob, targetBlob));
        assertFalse(cursorWindow.isNull(0, 4));
        // Test isBlob
        assertTrue(cursorWindow.isBlob(0, 4));
    }

    public void testCopyStringToBuffer() {
        int DEFAULT_ARRAY_LENGTH = 64;
        String baseString = "0123456789";
        String expectedString = "";
        // Create a 60 characters string.
        for (int i = 0; i < 6; i++) {
            expectedString += baseString;
        }
        CharArrayBuffer charArrayBuffer = new CharArrayBuffer(null);
        CursorWindow cursorWindow = new CursorWindow(true);
        cursorWindow.setNumColumns(2);
        cursorWindow.allocRow();

        assertEquals(null, charArrayBuffer.data);
        cursorWindow.putString(expectedString, 0, 0);
        cursorWindow.copyStringToBuffer(0, 0, charArrayBuffer);
        assertNotNull(charArrayBuffer.data);
        // By default, if the field's string is shorter than 64, array will be allocated as 64.
        assertEquals(DEFAULT_ARRAY_LENGTH, charArrayBuffer.data.length);
        assertEquals(expectedString,
                new String(charArrayBuffer.data, 0, charArrayBuffer.sizeCopied));

        // Test in case of string is longer than 64,
        expectedString += baseString;
        charArrayBuffer = new CharArrayBuffer(null);
        cursorWindow.putString(expectedString, 0, 1);
        cursorWindow.copyStringToBuffer(0, 1, charArrayBuffer);
        assertNotNull(charArrayBuffer.data);
        // If the string is longer than 64, array will be allocated as needed(longer than 64).
        assertEquals(expectedString,
                new String(charArrayBuffer.data, 0, charArrayBuffer.sizeCopied));
        assertEquals(70, expectedString.length());
        assertEquals(expectedString.length(), charArrayBuffer.data.length);
    }

    public void testAccessStartPosition() {
        final int TEST_POSITION_1 = 0;
        final int TEST_POSITION_2 = 3;

        CursorWindow cursorWindow = new CursorWindow(true);
        fillCursorTestContents(cursorWindow, 5);

        // Test setStartPosition
        assertEquals(TEST_POSITION_1, cursorWindow.getStartPosition());
        assertEquals(3, cursorWindow.getInt(3, 0));
        assertEquals(TEST_STRING + "3", cursorWindow.getString(3, 1));
        assertEquals(4, cursorWindow.getInt(4, 0));
        assertEquals(TEST_STRING + "4", cursorWindow.getString(4, 1));
        cursorWindow.setStartPosition(TEST_POSITION_2);

        assertEquals(TEST_POSITION_2, cursorWindow.getStartPosition());

        assertEquals(0, cursorWindow.getInt(3, 0));
        assertEquals(TEST_STRING + "0", cursorWindow.getString(3, 1));
        assertEquals(1, cursorWindow.getInt(4, 0));
        assertEquals(TEST_STRING + "1", cursorWindow.getString(4, 1));
        try {
            cursorWindow.getBlob(0, 0);
            fail("Row number is smaller than startPosition, will cause a IllegalStateException.");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testClearAndOnAllReferencesReleased() {
        MockCursorWindow cursorWindow = new MockCursorWindow(true);

        assertEquals(0, cursorWindow.getNumRows());
        fillCursorTestContents(cursorWindow, 10);
        assertEquals(10, cursorWindow.getNumRows());
        assertEquals(0, cursorWindow.getStartPosition());
        cursorWindow.setStartPosition(5);
        assertEquals(5, cursorWindow.getStartPosition());

        // Test clear(). a complete calling process of cursorWindow has a perfect acquiring and
        // releasing pair, so the references number will be equal at the begin and the end.
        assertFalse(cursorWindow.hasReleasedAllReferences());
        cursorWindow.clear();
        assertEquals(0, cursorWindow.getNumRows());
        assertEquals(0, cursorWindow.getStartPosition());
        assertFalse(cursorWindow.hasReleasedAllReferences());

        // Test onAllReferencesReleased.
        // By default, cursorWindow's reference is 1, when it reachs 0, onAllReferencesReleased
        // be invoked.
        cursorWindow = new MockCursorWindow(true);
        cursorWindow.releaseReference();
        assertTrue(cursorWindow.hasReleasedAllReferences());
    }

    public void testDescribeContents() {
        CursorWindow cursorWindow = new CursorWindow(true);
        assertEquals(0, cursorWindow.describeContents());
    }

    private class MockCursorWindow extends CursorWindow {
        private boolean mHasReleasedAllReferences = false;

        public MockCursorWindow(boolean localWindow) {
            super(localWindow);
        }

        @Override
        protected void onAllReferencesReleased() {
            super.onAllReferencesReleased();
            mHasReleasedAllReferences = true;
        }

        public boolean hasReleasedAllReferences() {
            return mHasReleasedAllReferences;
        }

        public void resetStatus() {
            mHasReleasedAllReferences = false;
        }
    }

    private void fillCursorTestContents(CursorWindow cursorWindow, int length) {
        cursorWindow.clear();
        cursorWindow.setStartPosition(0);
        cursorWindow.setNumColumns(2);
        for (int i = 0; i < length; i++) {
            cursorWindow.allocRow();
            cursorWindow.putLong(i, i, 0);
            cursorWindow.putString(TEST_STRING + i, i, 1);
        }
    }

    private static ArrayList<ArrayList<Integer>> createTestList(int rows, int cols) {
        ArrayList<ArrayList<Integer>> list = new ArrayList<ArrayList<Integer>>();
        Random generator = new Random();

        for (int i = 0; i < rows; i++) {
            ArrayList<Integer> col = new ArrayList<Integer>();
            list.add(col);
            for (int j = 0; j < cols; j++) {
                // generate random number
                col.add(j == 0 ? i : generator.nextInt());
            }
        }
        return list;
    }

    /**
     * The method comes from unit_test CursorWindowTest.
     */
    private CursorWindow getOneByOneWindow() {
        CursorWindow window = new CursorWindow(false);
        assertTrue(window.setNumColumns(1));
        assertTrue(window.allocRow());
        return window;
    }
}
