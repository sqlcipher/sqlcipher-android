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
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.test.AndroidTestCase;

import net.zetetic.database.sqlcipher.SQLiteCursor;
import net.zetetic.database.sqlcipher.SQLiteCursorDriver;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteQuery;
import net.zetetic.database.sqlcipher.SQLiteQueryBuilder;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class SQLiteQueryBuilderTest extends AndroidTestCase {
    private SQLiteDatabase mDatabase;
    private final String TEST_TABLE_NAME = "test";
    private final String EMPLOYEE_TABLE_NAME = "employee";
    private static final String DATABASE_FILE = "database_test.db";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        System.loadLibrary("sqlcipher");
        File f = mContext.getDatabasePath(DATABASE_FILE);
        f.mkdirs();
        if (f.exists()) { f.delete(); }
        mDatabase = SQLiteDatabase.openOrCreateDatabase(f,null);
        assertNotNull(mDatabase);
    }

    @Override
    protected void tearDown() throws Exception {
        mDatabase.close();
        getContext().deleteDatabase(DATABASE_FILE);
        super.tearDown();
    }

    public void testConstructor() {
        new SQLiteQueryBuilder();
    }

    public void testSetDistinct() {
        String expected;
        SQLiteQueryBuilder sqliteQueryBuilder = new SQLiteQueryBuilder();
        sqliteQueryBuilder.setTables(TEST_TABLE_NAME);
        sqliteQueryBuilder.setDistinct(false);
        sqliteQueryBuilder.appendWhere("age=20");
        String sql = sqliteQueryBuilder.buildQuery(new String[] { "age", "address" },
                null, null, null, null, null, null);
        assertEquals(TEST_TABLE_NAME, sqliteQueryBuilder.getTables());
        expected = "SELECT age, address FROM " + TEST_TABLE_NAME + " WHERE (age=20)";
        assertEquals(expected, sql);

        sqliteQueryBuilder = new SQLiteQueryBuilder();
        sqliteQueryBuilder.setTables(EMPLOYEE_TABLE_NAME);
        sqliteQueryBuilder.setDistinct(true);
        sqliteQueryBuilder.appendWhere("age>32");
        sql = sqliteQueryBuilder.buildQuery(new String[] { "age", "address" },
                null, null, null, null, null, null);
        assertEquals(EMPLOYEE_TABLE_NAME, sqliteQueryBuilder.getTables());
        expected = "SELECT DISTINCT age, address FROM " + EMPLOYEE_TABLE_NAME + " WHERE (age>32)";
        assertEquals(expected, sql);

        sqliteQueryBuilder = new SQLiteQueryBuilder();
        sqliteQueryBuilder.setTables(EMPLOYEE_TABLE_NAME);
        sqliteQueryBuilder.setDistinct(true);
        sqliteQueryBuilder.appendWhereEscapeString("age>32");
        sql = sqliteQueryBuilder.buildQuery(new String[] { "age", "address" },
                null, null, null, null, null, null);
        assertEquals(EMPLOYEE_TABLE_NAME, sqliteQueryBuilder.getTables());
        expected = "SELECT DISTINCT age, address FROM " + EMPLOYEE_TABLE_NAME
                + " WHERE ('age>32')";
        assertEquals(expected, sql);
    }

    public void testSetProjectionMap() {
        String expected;
        Map<String, String> projectMap = new HashMap<String, String>();
        projectMap.put("EmployeeName", "name");
        projectMap.put("EmployeeAge", "age");
        projectMap.put("EmployeeAddress", "address");
        SQLiteQueryBuilder sqliteQueryBuilder = new SQLiteQueryBuilder();
        sqliteQueryBuilder.setTables(TEST_TABLE_NAME);
        sqliteQueryBuilder.setDistinct(false);
        sqliteQueryBuilder.setProjectionMap(projectMap);
        String sql = sqliteQueryBuilder.buildQuery(new String[] { "EmployeeName", "EmployeeAge" },
                null, null, null, null, null, null);
        expected = "SELECT name, age FROM " + TEST_TABLE_NAME;
        assertEquals(expected, sql);

        sql = sqliteQueryBuilder.buildQuery(null, // projectionIn is null
                null, null, null, null, null, null);
        assertTrue(sql.matches("SELECT (age|name|address), (age|name|address), (age|name|address) "
                + "FROM " + TEST_TABLE_NAME));
        assertTrue(sql.contains("age"));
        assertTrue(sql.contains("name"));
        assertTrue(sql.contains("address"));

        sqliteQueryBuilder.setProjectionMap(null);
        sql = sqliteQueryBuilder.buildQuery(new String[] { "name", "address" },
                null, null, null, null, null, null);
        assertTrue(sql.matches("SELECT (name|address), (name|address) "
                + "FROM " + TEST_TABLE_NAME));
        assertTrue(sql.contains("name"));
        assertTrue(sql.contains("address"));
    }

    public void testSetCursorFactory() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, " +
                "name TEXT, age INTEGER, address TEXT);");
        mDatabase.execSQL("INSERT INTO test (name, age, address) VALUES ('Mike', '20', 'LA');");
        mDatabase.execSQL("INSERT INTO test (name, age, address) VALUES ('jack', '40', 'LA');");

        SQLiteQueryBuilder sqliteQueryBuilder = new SQLiteQueryBuilder();
        sqliteQueryBuilder.setTables(TEST_TABLE_NAME);
        Cursor cursor = sqliteQueryBuilder.query(mDatabase, new String[] { "name", "age" },
                null, null, null, null, null);
        assertNotNull(cursor);
        assertTrue(cursor instanceof SQLiteCursor);

        SQLiteDatabase.CursorFactory factory = new SQLiteDatabase.CursorFactory() {
            public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
                    String editTable, SQLiteQuery query) {
                return new MockCursor(db, masterQuery, editTable, query);
            }
        };

        sqliteQueryBuilder.setCursorFactory(factory);
        cursor = sqliteQueryBuilder.query(mDatabase, new String[] { "name", "age" },
                null, null, null, null, null);
        assertNotNull(cursor);
        assertTrue(cursor instanceof MockCursor);
    }

    private static class MockCursor extends SQLiteCursor {
        public MockCursor(SQLiteDatabase db, SQLiteCursorDriver driver,
                String editTable, SQLiteQuery query) {
            super(db, driver, editTable, query);
        }
    }

    public void testBuildQueryString() {
        String expected;
        final String[] DEFAULT_TEST_PROJECTION = new String [] { "name", "age", "sum(salary)" };
        final String DEFAULT_TEST_WHERE = "age > 25";
        final String DEFAULT_HAVING = "sum(salary) > 3000";

        String sql = SQLiteQueryBuilder.buildQueryString(false, "Employee",
                DEFAULT_TEST_PROJECTION,
                DEFAULT_TEST_WHERE, "name", DEFAULT_HAVING, "name", "100");

        expected = "SELECT name, age, sum(salary) FROM Employee WHERE " + DEFAULT_TEST_WHERE +
                " GROUP BY name " +
                "HAVING " + DEFAULT_HAVING + " " +
                "ORDER BY name " +
                "LIMIT 100";
        assertEquals(expected, sql);
    }

    public void testBuildQuery() {
        final String[] DEFAULT_TEST_PROJECTION = new String[] { "name", "sum(salary)" };
        final String DEFAULT_TEST_WHERE = "age > 25";
        final String DEFAULT_HAVING = "sum(salary) > 2000";

        SQLiteQueryBuilder sqliteQueryBuilder = new SQLiteQueryBuilder();
        sqliteQueryBuilder.setTables(TEST_TABLE_NAME);
        sqliteQueryBuilder.setDistinct(false);
        String sql = sqliteQueryBuilder.buildQuery(DEFAULT_TEST_PROJECTION,
                DEFAULT_TEST_WHERE, null, "name", DEFAULT_HAVING, "name", "2");
        String expected = "SELECT name, sum(salary) FROM " + TEST_TABLE_NAME
                + " WHERE (" + DEFAULT_TEST_WHERE + ") " +
                "GROUP BY name HAVING " + DEFAULT_HAVING + " ORDER BY name LIMIT 2";
        assertEquals(expected, sql);
    }

    public void testAppendColumns() {
        StringBuilder sb = new StringBuilder();
        String[] columns = new String[] { "name", "age" };

        assertEquals("", sb.toString());
        SQLiteQueryBuilder.appendColumns(sb, columns);
        assertEquals("name, age ", sb.toString());
    }

    public void testQuery() {
        createEmployeeTable();

        SQLiteQueryBuilder sqliteQueryBuilder = new SQLiteQueryBuilder();
        sqliteQueryBuilder.setTables("Employee");
        Cursor cursor = sqliteQueryBuilder.query(mDatabase,
                new String[] { "name", "sum(salary)" }, null, null,
                "name", "sum(salary)>1000", "name");
        assertNotNull(cursor);
        assertEquals(3, cursor.getCount());

        final int COLUMN_NAME_INDEX = 0;
        final int COLUMN_SALARY_INDEX = 1;
        cursor.moveToFirst();
        assertEquals("Jim", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(4500, cursor.getInt(COLUMN_SALARY_INDEX));
        cursor.moveToNext();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(4000, cursor.getInt(COLUMN_SALARY_INDEX));
        cursor.moveToNext();
        assertEquals("jack", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(3500, cursor.getInt(COLUMN_SALARY_INDEX));

        sqliteQueryBuilder = new SQLiteQueryBuilder();
        sqliteQueryBuilder.setTables(EMPLOYEE_TABLE_NAME);
        cursor = sqliteQueryBuilder.query(mDatabase,
                new String[] { "name", "sum(salary)" }, null, null,
                "name", "sum(salary)>1000", "name", "2" // limit is 2
                );
        assertNotNull(cursor);
        assertEquals(2, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Jim", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(4500, cursor.getInt(COLUMN_SALARY_INDEX));
        cursor.moveToNext();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(4000, cursor.getInt(COLUMN_SALARY_INDEX));
    }

    public void testUnionQuery() {
        String expected;
        String[] innerProjection = new String[] {"name", "age", "location"};
        SQLiteQueryBuilder employeeQueryBuilder = new SQLiteQueryBuilder();
        SQLiteQueryBuilder peopleQueryBuilder = new SQLiteQueryBuilder();

        employeeQueryBuilder.setTables("employee");
        peopleQueryBuilder.setTables("people");

        String employeeSubQuery = employeeQueryBuilder.buildUnionSubQuery(
                "_id", innerProjection,
                null, 2, "employee",
                "age=25",
                null, null, null);
        String peopleSubQuery = peopleQueryBuilder.buildUnionSubQuery(
                "_id", innerProjection,
                null, 2, "people",
                "location=LA",
                null, null, null);
        expected = "SELECT name, age, location FROM employee WHERE (age=25)";
        assertEquals(expected, employeeSubQuery);
        expected = "SELECT name, age, location FROM people WHERE (location=LA)";
        assertEquals(expected, peopleSubQuery);

        SQLiteQueryBuilder unionQueryBuilder = new SQLiteQueryBuilder();

        unionQueryBuilder.setDistinct(true);

        String unionQuery = unionQueryBuilder.buildUnionQuery(
                new String[] { employeeSubQuery, peopleSubQuery }, null, null);
        expected = "SELECT name, age, location FROM employee WHERE (age=25) " +
                "UNION SELECT name, age, location FROM people WHERE (location=LA)";
        assertEquals(expected, unionQuery);
    }

    public void testCancelableQuery_WhenNotCanceled_ReturnsResultSet() {
        createEmployeeTable();

        CancellationSignal cancellationSignal = new CancellationSignal();
        SQLiteQueryBuilder sqliteQueryBuilder = new SQLiteQueryBuilder();
        sqliteQueryBuilder.setTables("Employee");
        Cursor cursor = sqliteQueryBuilder.query(mDatabase,
                new String[] { "name", "sum(salary)" }, null, null,
                "name", "sum(salary)>1000", "name", null, cancellationSignal);

        assertEquals(3, cursor.getCount());
    }

    public void testCancelableQuery_WhenCanceledBeforeQuery_ThrowsImmediately() {
        createEmployeeTable();

        CancellationSignal cancellationSignal = new CancellationSignal();
        SQLiteQueryBuilder sqliteQueryBuilder = new SQLiteQueryBuilder();
        sqliteQueryBuilder.setTables("Employee");

        cancellationSignal.cancel();
        try {
            sqliteQueryBuilder.query(mDatabase,
                    new String[] { "name", "sum(salary)" }, null, null,
                    "name", "sum(salary)>1000", "name", null, cancellationSignal);
            fail("Expected OperationCanceledException");
        } catch (OperationCanceledException ex) {
            // expected
        }
    }

    public void testCancelableQuery_WhenCanceledAfterQuery_ThrowsWhenExecuted() {
        createEmployeeTable();

        CancellationSignal cancellationSignal = new CancellationSignal();
        SQLiteQueryBuilder sqliteQueryBuilder = new SQLiteQueryBuilder();
        sqliteQueryBuilder.setTables("Employee");

        Cursor cursor = sqliteQueryBuilder.query(mDatabase,
                new String[] { "name", "sum(salary)" }, null, null,
                "name", "sum(salary)>1000", "name", null, cancellationSignal);

        cancellationSignal.cancel();
        try {
            cursor.getCount(); // force execution
            fail("Expected OperationCanceledException");
        } catch (OperationCanceledException ex) {
            // expected
        }
    }

    public void testCancelableQuery_WhenCanceledDueToContention_StopsWaitingAndThrows() {
        createEmployeeTable();

        for (int i = 0; i < 5; i++) {
            final CancellationSignal cancellationSignal = new CancellationSignal();
            final Semaphore barrier1 = new Semaphore(0);
            final Semaphore barrier2 = new Semaphore(0);
            Thread contentionThread = new Thread() {
                @Override
                public void run() {
                    mDatabase.beginTransaction(); // acquire the only available connection
                    barrier1.release(); // release query to start running
                    try {
                        barrier2.acquire(); // wait for test to end
                    } catch (InterruptedException e) {
                    }
                    mDatabase.endTransaction(); // release the connection
                }
            };
            Thread cancellationThread = new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ex) {
                    }
                    cancellationSignal.cancel();
                }
            };
            try {
                SQLiteQueryBuilder sqliteQueryBuilder = new SQLiteQueryBuilder();
                sqliteQueryBuilder.setTables("Employee");

                contentionThread.start();
                cancellationThread.start();

                try {
                    barrier1.acquire(); // wait for contention thread to start transaction
                } catch (InterruptedException e) {
                }

                final long startTime = System.nanoTime();
                try {
                    Cursor cursor = sqliteQueryBuilder.query(mDatabase,
                            new String[] { "name", "sum(salary)" }, null, null,
                            "name", "sum(salary)>1000", "name", null, cancellationSignal);
                    cursor.getCount(); // force execution
                    fail("Expected OperationCanceledException");
                } catch (OperationCanceledException ex) {
                    // expected
                }

                // We want to confirm that the query really was blocked trying to acquire a
                // connection for a certain amount of time before it was freed by cancel.
                final long waitTime = System.nanoTime() - startTime;
                if (waitTime > 150 * 1000000L) {
                    return; // success!
                }
            } finally {
                barrier1.release();
                barrier2.release();
                try {
                    contentionThread.join();
                    cancellationThread.join();
                } catch (InterruptedException e) {
                }
            }
        }

        // Occasionally we might miss the timing deadline due to factors in the
        // environment, but if after several trials we still couldn't demonstrate
        // that the query was blocked, then the test must be broken.
        fail("Could not prove that the query actually blocked before cancel() was called.");
    }

    public void testCancelableQuery_WhenCanceledDuringLongRunningQuery_CancelsQueryAndThrows() {
        // Populate a table with a bunch of integers.
        mDatabase.execSQL("CREATE TABLE x (v INTEGER);");
        for (int i = 0; i < 100; i++) {
            mDatabase.execSQL("INSERT INTO x VALUES (?)", new Object[] { i });
        }

        for (int i = 0; i < 5; i++) {
            final CancellationSignal cancellationSignal = new CancellationSignal();
            Thread cancellationThread = new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ex) {
                    }
                    cancellationSignal.cancel();
                }
            };
            try {
                // Build an unsatisfiable 5-way cross-product query over 100 values but
                // produces no output.  This should force SQLite to loop for a long time
                // as it tests 10^10 combinations.
                SQLiteQueryBuilder sqliteQueryBuilder = new SQLiteQueryBuilder();
                sqliteQueryBuilder.setTables("x AS a, x AS b, x AS c, x AS d, x AS e");

                cancellationThread.start();

                final long startTime = System.nanoTime();
                try {
                    Cursor cursor = sqliteQueryBuilder.query(mDatabase, null,
                            "a.v + b.v + c.v + d.v + e.v > 1000000",
                            null, null, null, null, null, cancellationSignal);
                    cursor.getCount(); // force execution
                    fail("Expected OperationCanceledException");
                } catch (OperationCanceledException ex) {
                    // expected
                }

                // We want to confirm that the query really was running and then got
                // canceled midway.
                final long waitTime = System.nanoTime() - startTime;
                if (waitTime > 150 * 1000000L && waitTime < 600 * 1000000L) {
                    return; // success!
                }
            } finally {
                try {
                    cancellationThread.join();
                } catch (InterruptedException e) {
                }
            }
        }

        // Occasionally we might miss the timing deadline due to factors in the
        // environment, but if after several trials we still couldn't demonstrate
        // that the query was canceled, then the test must be broken.
        fail("Could not prove that the query actually canceled midway during execution.");
    }

    private void createEmployeeTable() {
        mDatabase.execSQL("CREATE TABLE employee (_id INTEGER PRIMARY KEY, " +
                "name TEXT, month INTEGER, salary INTEGER);");
        mDatabase.execSQL("INSERT INTO employee (name, month, salary) " +
                "VALUES ('Mike', '1', '1000');");
        mDatabase.execSQL("INSERT INTO employee (name, month, salary) " +
                "VALUES ('Mike', '2', '3000');");
        mDatabase.execSQL("INSERT INTO employee (name, month, salary) " +
                "VALUES ('jack', '1', '2000');");
        mDatabase.execSQL("INSERT INTO employee (name, month, salary) " +
                "VALUES ('jack', '3', '1500');");
        mDatabase.execSQL("INSERT INTO employee (name, month, salary) " +
                "VALUES ('Jim', '1', '1000');");
        mDatabase.execSQL("INSERT INTO employee (name, month, salary) " +
                "VALUES ('Jim', '3', '3500');");
    }
}
