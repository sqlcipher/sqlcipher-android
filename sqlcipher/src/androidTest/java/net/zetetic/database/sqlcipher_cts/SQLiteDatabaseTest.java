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
import android.database.SQLException;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

import net.zetetic.database.DatabaseUtils;
import net.zetetic.database.sqlcipher.SQLiteCursor;
import net.zetetic.database.sqlcipher.SQLiteCursorDriver;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabase.CursorFactory;
import net.zetetic.database.sqlcipher.SQLiteQuery;
import net.zetetic.database.sqlcipher.SQLiteStatement;
import net.zetetic.database.sqlcipher.SQLiteTransactionListener;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;

public class SQLiteDatabaseTest extends AndroidTestCase {
    private SQLiteDatabase mDatabase;
    private File mDatabaseFile;
    private String mDatabaseFilePath;
    private String mDatabaseDir;

    private boolean mTransactionListenerOnBeginCalled;
    private boolean mTransactionListenerOnCommitCalled;
    private boolean mTransactionListenerOnRollbackCalled;

    private static final String DATABASE_FILE_NAME = "database_test.db";
    private static final String TABLE_NAME = "test";
    private static final int COLUMN_ID_INDEX = 0;
    private static final int COLUMN_NAME_INDEX = 1;
    private static final int COLUMN_AGE_INDEX = 2;
    private static final int COLUMN_ADDR_INDEX = 3;
    private static final String[] TEST_PROJECTION = new String[] {
            "_id",      // 0
            "name",     // 1
            "age",      // 2
            "address"   // 3
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        System.loadLibrary("sqlcipher");
        getContext().deleteDatabase(DATABASE_FILE_NAME);
        mDatabaseFilePath = getContext().getDatabasePath(DATABASE_FILE_NAME).getPath();
        mDatabaseFile = getContext().getDatabasePath(DATABASE_FILE_NAME);
        mDatabaseDir = mDatabaseFile.getParent();
        mDatabaseFile.getParentFile().mkdirs(); // directory may not exist
        mDatabase = SQLiteDatabase.openOrCreateDatabase(mDatabaseFile, null);
        assertNotNull(mDatabase);

        mTransactionListenerOnBeginCalled = false;
        mTransactionListenerOnCommitCalled = false;
        mTransactionListenerOnRollbackCalled = false;
    }

    @Override
    protected void tearDown() throws Exception {
        mDatabase.close();
        mDatabaseFile.delete();
        super.tearDown();
    }

    public void testOpenDatabase() {
        CursorFactory factory = new CursorFactory() {
            public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
                    String editTable, SQLiteQuery query) {
                return new MockSQLiteCursor(db, masterQuery, editTable, query);
            }
        };

        SQLiteDatabase db = SQLiteDatabase.openDatabase(mDatabaseFilePath,
                factory, SQLiteDatabase.CREATE_IF_NECESSARY);
        assertNotNull(db);
        db.close();

        File dbFile = new File(mDatabaseDir, "database_test12345678.db");
        dbFile.delete();
        assertFalse(dbFile.exists());
        db = SQLiteDatabase.openOrCreateDatabase(dbFile.getPath(), factory);
        assertNotNull(db);
        db.close();
        dbFile.delete();

        dbFile = new File(mDatabaseDir, DATABASE_FILE_NAME);
        db = SQLiteDatabase.openOrCreateDatabase(dbFile, factory);
        assertNotNull(db);
        db.close();
        dbFile.delete();

        db = SQLiteDatabase.create(factory);
        assertNotNull(db);
        db.close();
    }

    public void testDeleteDatabase() throws IOException {
        File dbFile = new File(mDatabaseDir, "database_test12345678.db");
        File journalFile = new File(dbFile.getPath() + "-journal");
        File shmFile = new File(dbFile.getPath() + "-shm");
        File walFile = new File(dbFile.getPath() + "-wal");
        File mjFile1 = new File(dbFile.getPath() + "-mj00000000");
        File mjFile2 = new File(dbFile.getPath() + "-mj00000001");
        File innocentFile = new File(dbFile.getPath() + "-innocent");

        dbFile.createNewFile();
        journalFile.createNewFile();
        shmFile.createNewFile();
        walFile.createNewFile();
        mjFile1.createNewFile();
        mjFile2.createNewFile();
        innocentFile.createNewFile();

        boolean deleted = SQLiteDatabase.deleteDatabase(dbFile);
        assertTrue(deleted);

        assertFalse(dbFile.exists());
        assertFalse(journalFile.exists());
        assertFalse(shmFile.exists());
        assertFalse(walFile.exists());
        assertFalse(mjFile1.exists());
        assertFalse(mjFile2.exists());
        assertTrue(innocentFile.exists());

        innocentFile.delete();

        boolean deletedAgain = SQLiteDatabase.deleteDatabase(dbFile);
        assertFalse(deletedAgain);
    }

    private class MockSQLiteCursor extends SQLiteCursor {
        public MockSQLiteCursor(SQLiteDatabase db, SQLiteCursorDriver driver,
                String editTable, SQLiteQuery query) {
            super(db, driver, editTable, query);
        }
    }

    public void testTransaction() {
        mDatabase.execSQL("CREATE TABLE test (num INTEGER);");
        mDatabase.execSQL("INSERT INTO test (num) VALUES (0)");

        // test execSQL without any explicit transactions.
        setNum(1);
        assertNum(1);

        // Test a single-level transaction.
        setNum(0);
        assertFalse(mDatabase.inTransaction());
        mDatabase.beginTransaction();
        assertTrue(mDatabase.inTransaction());
        setNum(1);
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
        assertFalse(mDatabase.inTransaction());
        assertNum(1);
        assertFalse(mDatabase.isDbLockedByCurrentThread());
        assertFalse(mDatabase.isDbLockedByOtherThreads());

        // Test a rolled-back transaction.
        setNum(0);
        assertFalse(mDatabase.inTransaction());
        mDatabase.beginTransaction();
        setNum(1);
        assertTrue(mDatabase.inTransaction());
        mDatabase.endTransaction();
        assertFalse(mDatabase.inTransaction());
        assertNum(0);
        assertFalse(mDatabase.isDbLockedByCurrentThread());
        assertFalse(mDatabase.isDbLockedByOtherThreads());

        // it should throw IllegalStateException if we end a non-existent transaction.
        assertThrowsIllegalState(new Runnable() {
            public void run() {
                mDatabase.endTransaction();
            }
        });

        // it should throw IllegalStateException if a set a non-existent transaction as clean.
        assertThrowsIllegalState(new Runnable() {
            public void run() {
                mDatabase.setTransactionSuccessful();
            }
        });

        mDatabase.beginTransaction();
        mDatabase.setTransactionSuccessful();
        // it should throw IllegalStateException if we mark a transaction as clean twice.
        assertThrowsIllegalState(new Runnable() {
            public void run() {
                mDatabase.setTransactionSuccessful();
            }
        });
        // it should throw IllegalStateException if we begin a transaction after marking the
        // parent as clean.
        assertThrowsIllegalState(new Runnable() {
            public void run() {
                mDatabase.beginTransaction();
            }
        });
        mDatabase.endTransaction();
        assertFalse(mDatabase.isDbLockedByCurrentThread());
        assertFalse(mDatabase.isDbLockedByOtherThreads());

        assertFalse(mDatabase.inTransaction());
        // Test a two-level transaction.
        setNum(0);
        mDatabase.beginTransaction();
        assertTrue(mDatabase.inTransaction());
        mDatabase.beginTransaction();
        assertTrue(mDatabase.inTransaction());
        setNum(1);
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
        assertTrue(mDatabase.inTransaction());
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
        assertFalse(mDatabase.inTransaction());
        assertNum(1);
        assertFalse(mDatabase.isDbLockedByCurrentThread());
        assertFalse(mDatabase.isDbLockedByOtherThreads());

        // Test rolling back an inner transaction.
        setNum(0);
        mDatabase.beginTransaction();
        mDatabase.beginTransaction();
        setNum(1);
        mDatabase.endTransaction();
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
        assertNum(0);
        assertFalse(mDatabase.isDbLockedByCurrentThread());
        assertFalse(mDatabase.isDbLockedByOtherThreads());

        // Test rolling back an outer transaction.
        setNum(0);
        mDatabase.beginTransaction();
        mDatabase.beginTransaction();
        setNum(1);
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
        mDatabase.endTransaction();
        assertNum(0);
        assertFalse(mDatabase.isDbLockedByCurrentThread());
        assertFalse(mDatabase.isDbLockedByOtherThreads());
    }

    private void setNum(int num) {
        mDatabase.execSQL("UPDATE test SET num = " + num);
    }

    private void assertNum(int num) {
        assertEquals(num, DatabaseUtils.longForQuery(mDatabase,
                "SELECT num FROM test", null));
    }

    private void assertThrowsIllegalState(Runnable r) {
        try {
            r.run();
            fail("did not throw expected IllegalStateException");
        } catch (IllegalStateException e) {
        }
    }

    public void testAccessMaximumSize() {
        long curMaximumSize = mDatabase.getMaximumSize();

        // the new maximum size is less than the current size.
        mDatabase.setMaximumSize(curMaximumSize - 1);
        assertEquals(curMaximumSize, mDatabase.getMaximumSize());

        // the new maximum size is more than the current size.
        mDatabase.setMaximumSize(curMaximumSize + 1);
        assertEquals(curMaximumSize + mDatabase.getPageSize(), mDatabase.getMaximumSize());
        assertTrue(mDatabase.getMaximumSize() > curMaximumSize);
    }

    public void testAccessPageSize() {
        File databaseFile = new File(mDatabaseDir, "database.db");
        if (databaseFile.exists()) {
            databaseFile.delete();
        }
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openOrCreateDatabase(databaseFile.getPath(), null);

            long initialValue = database.getPageSize();
            // check that this does not throw an exception
            // setting a different page size may not be supported after the DB has been created
            database.setPageSize(initialValue);
            assertEquals(initialValue, database.getPageSize());

        } finally {
            if (database != null) {
                database.close();
                databaseFile.delete();
            }
        }
    }

    public void testCompileStatement() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, "
                + "name TEXT, age INTEGER, address TEXT);");

        String name = "Mike";
        int age = 21;
        String address = "LA";

        // at the beginning, there is no record in the database.
        Cursor cursor = mDatabase.query("test", TEST_PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());

        String sql = "INSERT INTO test (name, age, address) VALUES (?, ?, ?);";
        SQLiteStatement insertStatement = mDatabase.compileStatement(sql);
        DatabaseUtils.bindObjectToProgram(insertStatement, 1, name);
        DatabaseUtils.bindObjectToProgram(insertStatement, 2, age);
        DatabaseUtils.bindObjectToProgram(insertStatement, 3, address);
        insertStatement.execute();
        insertStatement.close();
        cursor.close();

        cursor = mDatabase.query("test", TEST_PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToNext();
        assertEquals(name, cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(age, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals(address, cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        SQLiteStatement deleteStatement = mDatabase.compileStatement("DELETE FROM test");
        deleteStatement.execute();

        cursor = mDatabase.query("test", null, null, null, null, null, null);
        assertEquals(0, cursor.getCount());
        cursor.deactivate();
        deleteStatement.close();
        cursor.close();
    }

    public void testDelete() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, "
                + "name TEXT, age INTEGER, address TEXT);");
        mDatabase.execSQL("INSERT INTO test (name, age, address) VALUES ('Mike', 20, 'LA');");
        mDatabase.execSQL("INSERT INTO test (name, age, address) VALUES ('Jack', 30, 'London');");
        mDatabase.execSQL("INSERT INTO test (name, age, address) VALUES ('Jim', 35, 'Chicago');");

        // delete one record.
        int count = mDatabase.delete(TABLE_NAME, "name = 'Mike'", null);
        assertEquals(1, count);

        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null,
                null, null, null, null);
        assertNotNull(cursor);
        // there are 2 records here.
        assertEquals(2, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Jack", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(30, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("London", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.moveToNext();
        assertEquals("Jim", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(35, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("Chicago", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        // delete another record.
        count = mDatabase.delete(TABLE_NAME, "name = ?", new String[] { "Jack" });
        assertEquals(1, count);

        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null,
                null, null);
        assertNotNull(cursor);
        // there are 1 records here.
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Jim", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(35, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("Chicago", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        mDatabase.execSQL("INSERT INTO test (name, age, address) VALUES ('Mike', 20, 'LA');");
        mDatabase.execSQL("INSERT INTO test (name, age, address) VALUES ('Jack', 30, 'London');");

        // delete all records.
        count = mDatabase.delete(TABLE_NAME, null, null);
        assertEquals(3, count);

        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());
        cursor.close();
    }

    public void testExecSQL() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, "
                + "name TEXT, age INTEGER, address TEXT);");

        // add a new record.
        mDatabase.execSQL("INSERT INTO test (name, age, address) VALUES ('Mike', 20, 'LA');");

        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null,
                null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(20, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("LA", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        // add other new record.
        mDatabase.execSQL("INSERT INTO test (name, age, address) VALUES ('Jack', 30, 'London');");

        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(2, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(20, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("LA", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.moveToNext();
        assertEquals("Jack", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(30, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("London", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        // delete a record.
        mDatabase.execSQL("DELETE FROM test WHERE name = ?;", new String[] { "Jack" });

        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(20, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("LA", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        // delete a non-exist record.
        mDatabase.execSQL("DELETE FROM test WHERE name = ?;", new String[] { "Wrong Name" });

        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(20, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("LA", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        try {
            // execSQL can not use for query.
            mDatabase.execSQL("SELECT * FROM test;");
            fail("should throw SQLException.");
        } catch (SQLException e) {
        }

        // make sure execSQL can't be used to execute more than 1 sql statement at a time
        mDatabase.execSQL("UPDATE test SET age = 40 WHERE name = 'Mike';" + 
                "UPDATE test SET age = 50 WHERE name = 'Mike';");
        // age should be updated to 40 not to 50
        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(40, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("LA", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        // make sure sql injection is NOT allowed or has no effect when using query()
        String harmfulQuery = "name = 'Mike';UPDATE test SET age = 50 WHERE name = 'Mike'";
        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, harmfulQuery, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        // row's age column SHOULD NOT be 50
        assertEquals(40, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("LA", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();;
    }

    public void testFindEditTable() {
        String tables = "table1 table2 table3";
        assertEquals("table1", SQLiteDatabase.findEditTable(tables));

        tables = "table1,table2,table3";
        assertEquals("table1", SQLiteDatabase.findEditTable(tables));

        tables = "table1";
        assertEquals("table1", SQLiteDatabase.findEditTable(tables));

        try {
            SQLiteDatabase.findEditTable("");
            fail("should throw IllegalStateException.");
        } catch (IllegalStateException e) {
        }
    }

    public void testGetPath() {
        assertEquals(mDatabaseFilePath, mDatabase.getPath());
    }

    public void testAccessVersion() {
        mDatabase.setVersion(1);
        assertEquals(1, mDatabase.getVersion());

        mDatabase.setVersion(3);
        assertEquals(3, mDatabase.getVersion());
    }

    public void testInsert() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, "
                + "name TEXT, age INTEGER, address TEXT);");

        ContentValues values = new ContentValues();
        values.put("name", "Jack");
        values.put("age", 20);
        values.put("address", "LA");
        mDatabase.insert(TABLE_NAME, "name", values);

        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null,
                null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Jack", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(20, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("LA", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        mDatabase.insert(TABLE_NAME, "name", null);
        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null,
                null, null);
        assertNotNull(cursor);
        assertEquals(2, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Jack", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(20, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("LA", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.moveToNext();
        assertNull(cursor.getString(COLUMN_NAME_INDEX));
        cursor.close();

        values = new ContentValues();
        values.put("Wrong Key", "Wrong value");
        mDatabase.insert(TABLE_NAME, "name", values);
        // there are still 2 records.
        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null,
                null, null);
        assertNotNull(cursor);
        assertEquals(2, cursor.getCount());
        cursor.close();

        // delete all record.
        mDatabase.execSQL("DELETE FROM test;");

        values = new ContentValues();
        values.put("name", "Mike");
        values.put("age", 30);
        values.put("address", "London");
        mDatabase.insertOrThrow(TABLE_NAME, "name", values);

        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null,
                null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(30, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("London", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        mDatabase.insertOrThrow(TABLE_NAME, "name", null);
        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null,
                null, null);
        assertNotNull(cursor);
        assertEquals(2, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(30, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("London", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.moveToNext();
        assertNull(cursor.getString(COLUMN_NAME_INDEX));
        cursor.close();

        values = new ContentValues();
        values.put("Wrong Key", "Wrong value");
        try {
            mDatabase.insertOrThrow(TABLE_NAME, "name", values);
            fail("should throw SQLException.");
        } catch (SQLException e) {
        }
    }

    public void testIsOpen() {
        assertTrue(mDatabase.isOpen());

        mDatabase.close();
        assertFalse(mDatabase.isOpen());
    }

    public void testIsReadOnly() {
        assertFalse(mDatabase.isReadOnly());

        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(mDatabaseFilePath, null,
                    SQLiteDatabase.OPEN_READONLY);
            assertTrue(database.isReadOnly());
        } finally {
            if (database != null) {
                database.close();
            }
        }
    }

    public void testReleaseMemory() {
        SQLiteDatabase.releaseMemory();
    }

    public void testSetLockingEnabled() {
        mDatabase.execSQL("CREATE TABLE test (num INTEGER);");
        mDatabase.execSQL("INSERT INTO test (num) VALUES (0)");

        mDatabase.setLockingEnabled(false);

        mDatabase.beginTransaction();
        setNum(1);
        assertNum(1);
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
    }

    @SuppressWarnings("deprecation")
    public void testYieldIfContendedWhenNotContended() {
        assertFalse(mDatabase.yieldIfContended());

        mDatabase.execSQL("CREATE TABLE test (num INTEGER);");
        mDatabase.execSQL("INSERT INTO test (num) VALUES (0)");

        // Make sure that things work outside an explicit transaction.
        setNum(1);
        assertNum(1);

        setNum(0);
        assertFalse(mDatabase.inTransaction());
        mDatabase.beginTransaction();
        assertTrue(mDatabase.inTransaction());
        assertFalse(mDatabase.yieldIfContended());
        setNum(1);
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();

        mDatabase.beginTransaction();
        assertTrue(mDatabase.inTransaction());
        assertFalse(mDatabase.yieldIfContendedSafely());
        setNum(1);
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
    }

    @SuppressWarnings("deprecation")
    public void testYieldIfContendedWhenContended() throws Exception {
        mDatabase.execSQL("CREATE TABLE test (num INTEGER);");
        mDatabase.execSQL("INSERT INTO test (num) VALUES (0)");

        // Begin a transaction and update a value.
        mDatabase.beginTransaction();
        setNum(1);
        assertNum(1);

        // On another thread, begin a transaction there.  This causes contention
        // for use of the database.  When the main thread yields, the second thread
        // begin its own transaction.  It should perceive the new state that was
        // committed by the main thread when it yielded.
        final Semaphore s = new Semaphore(0);
        Thread t = new Thread() {
            @Override
            public void run() {
                s.release(); // let main thread continue

                mDatabase.beginTransaction();
                assertNum(1);
                setNum(2);
                assertNum(2);
                mDatabase.setTransactionSuccessful();
                mDatabase.endTransaction();
            }
        };
        t.start();

        // Wait for thread to try to begin its transaction.
        s.acquire();
        Thread.sleep(500);

        // Yield.  There should be contention for the database now, so yield will
        // return true.
        assertTrue(mDatabase.yieldIfContendedSafely());

        // Since we reacquired the transaction, the other thread must have finished
        // its transaction.  We should observe its changes and our own within this transaction.
        assertNum(2);
        setNum(3);
        assertNum(3);

        // Go ahead and finish the transaction.
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
        assertNum(3);

        t.join();
    }

    public void testQuery() {
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

        Cursor cursor = mDatabase.query(true, "employee", new String[] { "name", "sum(salary)" },
                null, null, "name", "sum(salary)>1000", "name", null);
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
        cursor.close();

        CursorFactory factory = new CursorFactory() {
            public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
                    String editTable, SQLiteQuery query) {
                return new MockSQLiteCursor(db, masterQuery, editTable, query);
            }
        };
        cursor = mDatabase.queryWithFactory(factory, true, "employee",
                new String[] { "name", "sum(salary)" },
                null, null, "name", "sum(salary) > 1000", "name", null);
        assertNotNull(cursor);
        assertTrue(cursor instanceof MockSQLiteCursor);
        cursor.moveToFirst();
        assertEquals("Jim", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(4500, cursor.getInt(COLUMN_SALARY_INDEX));
        cursor.moveToNext();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(4000, cursor.getInt(COLUMN_SALARY_INDEX));
        cursor.moveToNext();
        assertEquals("jack", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(3500, cursor.getInt(COLUMN_SALARY_INDEX));
        cursor.close();

        cursor = mDatabase.query("employee", new String[] { "name", "sum(salary)" },
                null, null, "name", "sum(salary) <= 4000", "name");
        assertNotNull(cursor);
        assertEquals(2, cursor.getCount());

        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(4000, cursor.getInt(COLUMN_SALARY_INDEX));
        cursor.moveToNext();
        assertEquals("jack", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(3500, cursor.getInt(COLUMN_SALARY_INDEX));
        cursor.close();

        cursor = mDatabase.query("employee", new String[] { "name", "sum(salary)" },
                null, null, "name", "sum(salary) > 1000", "name", "2");
        assertNotNull(cursor);
        assertEquals(2, cursor.getCount());

        cursor.moveToFirst();
        assertEquals("Jim", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(4500, cursor.getInt(COLUMN_SALARY_INDEX));
        cursor.moveToNext();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(4000, cursor.getInt(COLUMN_SALARY_INDEX));
        cursor.close();

        String sql = "SELECT name, month FROM employee WHERE salary > ?;";
        cursor = mDatabase.rawQuery(sql, new String[] { "2000" });
        assertNotNull(cursor);
        assertEquals(2, cursor.getCount());

        final int COLUMN_MONTH_INDEX = 1;
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(2, cursor.getInt(COLUMN_MONTH_INDEX));
        cursor.moveToNext();
        assertEquals("Jim", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(3, cursor.getInt(COLUMN_MONTH_INDEX));
        cursor.close();

        cursor = mDatabase.rawQueryWithFactory(factory, sql, new String[] { "2000" }, null);
        assertNotNull(cursor);
        assertEquals(2, cursor.getCount());
        assertTrue(cursor instanceof MockSQLiteCursor);
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(2, cursor.getInt(COLUMN_MONTH_INDEX));
        cursor.moveToNext();
        assertEquals("Jim", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(3, cursor.getInt(COLUMN_MONTH_INDEX));
        cursor.close();
    }

    public void testReplace() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, "
                + "name TEXT, age INTEGER, address TEXT);");

        ContentValues values = new ContentValues();
        values.put("name", "Jack");
        values.put("age", 20);
        values.put("address", "LA");
        mDatabase.replace(TABLE_NAME, "name", values);

        Cursor cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION,
                null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        int id = cursor.getInt(COLUMN_ID_INDEX);
        assertEquals("Jack", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(20, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("LA", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        values = new ContentValues();
        values.put("_id", id);
        values.put("name", "Mike");
        values.put("age", 40);
        values.put("address", "London");
        mDatabase.replace(TABLE_NAME, "name", values);

        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount()); // there is still ONLY 1 record.
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(40, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("London", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        values = new ContentValues();
        values.put("name", "Jack");
        values.put("age", 20);
        values.put("address", "LA");
        mDatabase.replaceOrThrow(TABLE_NAME, "name", values);

        cursor = mDatabase.query(TABLE_NAME, TEST_PROJECTION, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(2, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Mike", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(40, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("London", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.moveToNext();
        assertEquals("Jack", cursor.getString(COLUMN_NAME_INDEX));
        assertEquals(20, cursor.getInt(COLUMN_AGE_INDEX));
        assertEquals("LA", cursor.getString(COLUMN_ADDR_INDEX));
        cursor.close();

        values = new ContentValues();
        values.put("Wrong Key", "Wrong value");
        try {
            mDatabase.replaceOrThrow(TABLE_NAME, "name", values);
            fail("should throw SQLException.");
        } catch (SQLException e) {
        }
    }

    public void testUpdate() {
        mDatabase.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, data TEXT);");

        mDatabase.execSQL("INSERT INTO test (data) VALUES ('string1');");
        mDatabase.execSQL("INSERT INTO test (data) VALUES ('string2');");
        mDatabase.execSQL("INSERT INTO test (data) VALUES ('string3');");

        String updatedString = "this is an updated test";
        ContentValues values = new ContentValues(1);
        values.put("data", updatedString);
        assertEquals(1, mDatabase.update("test", values, "_id=1", null));
        Cursor cursor = mDatabase.query("test", null, "_id=1", null, null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        String value = cursor.getString(cursor.getColumnIndexOrThrow("data"));
        assertEquals(updatedString, value);
        cursor.close();
    }

    public void testNeedUpgrade() {
        mDatabase.setVersion(0);
        assertTrue(mDatabase.needUpgrade(1));
        mDatabase.setVersion(1);
        assertFalse(mDatabase.needUpgrade(1));
    }

    public void testSetLocale() {
//        final String[] STRINGS = {
//                "c\u00f4t\u00e9",
//                "cote",
//                "c\u00f4te",
//                "cot\u00e9",
//                "boy",
//                "dog",
//                "COTE",
//        };
//
//        mDatabase.execSQL("CREATE TABLE test (data TEXT COLLATE LOCALIZED);");
//        for (String s : STRINGS) {
//            mDatabase.execSQL("INSERT INTO test VALUES('" + s + "');");
//        }
//
//        mDatabase.setLocale(new Locale("en", "US"));
//
//        String sql = "SELECT data FROM test ORDER BY data COLLATE LOCALIZED ASC";
//        Cursor cursor = mDatabase.rawQuery(sql, null);
//        assertNotNull(cursor);
//        ArrayList<String> items = new ArrayList<String>();
//        while (cursor.moveToNext()) {
//            items.add(cursor.getString(0));
//        }
//        String[] results = items.toArray(new String[items.size()]);
//        assertEquals(STRINGS.length, results.length);
//        cursor.close();
//
//        // The database code currently uses PRIMARY collation strength,
//        // meaning that all versions of a character compare equal (regardless
//        // of case or accents), leaving the "cote" flavors in database order.
//        MoreAsserts.assertEquals(results, new String[] {
//                STRINGS[4],  // "boy"
//                STRINGS[0],  // sundry forms of "cote"
//                STRINGS[1],
//                STRINGS[2],
//                STRINGS[3],
//                STRINGS[6],  // "COTE"
//                STRINGS[5],  // "dog"
//        });
    }

    public void testOnAllReferencesReleased() {
        assertTrue(mDatabase.isOpen());
        mDatabase.releaseReference();
        assertFalse(mDatabase.isOpen());
    }

    public void testTransactionWithSQLiteTransactionListener() {
        mDatabase.execSQL("CREATE TABLE test (num INTEGER);");
        mDatabase.execSQL("INSERT INTO test (num) VALUES (0)");

        assertEquals(mTransactionListenerOnBeginCalled, false);
        assertEquals(mTransactionListenerOnCommitCalled, false);
        assertEquals(mTransactionListenerOnRollbackCalled, false);
        mDatabase.beginTransactionWithListener(new TestSQLiteTransactionListener());

        // Assert that the transcation has started
        assertEquals(mTransactionListenerOnBeginCalled, true);
        assertEquals(mTransactionListenerOnCommitCalled, false);
        assertEquals(mTransactionListenerOnRollbackCalled, false);

        setNum(1);

        // State shouldn't have changed
        assertEquals(mTransactionListenerOnBeginCalled, true);
        assertEquals(mTransactionListenerOnCommitCalled, false);
        assertEquals(mTransactionListenerOnRollbackCalled, false);

        // commit the transaction
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();

        // the listener should have been told that commit was called
        assertEquals(mTransactionListenerOnBeginCalled, true);
        assertEquals(mTransactionListenerOnCommitCalled, true);
        assertEquals(mTransactionListenerOnRollbackCalled, false);
    }

    public void testRollbackTransactionWithSQLiteTransactionListener() {
        mDatabase.execSQL("CREATE TABLE test (num INTEGER);");
        mDatabase.execSQL("INSERT INTO test (num) VALUES (0)");

        assertEquals(mTransactionListenerOnBeginCalled, false);
        assertEquals(mTransactionListenerOnCommitCalled, false);
        assertEquals(mTransactionListenerOnRollbackCalled, false);
        mDatabase.beginTransactionWithListener(new TestSQLiteTransactionListener());

        // Assert that the transcation has started
        assertEquals(mTransactionListenerOnBeginCalled, true);
        assertEquals(mTransactionListenerOnCommitCalled, false);
        assertEquals(mTransactionListenerOnRollbackCalled, false);

        setNum(1);

        // State shouldn't have changed
        assertEquals(mTransactionListenerOnBeginCalled, true);
        assertEquals(mTransactionListenerOnCommitCalled, false);
        assertEquals(mTransactionListenerOnRollbackCalled, false);

        // commit the transaction
        mDatabase.endTransaction();

        // the listener should have been told that commit was called
        assertEquals(mTransactionListenerOnBeginCalled, true);
        assertEquals(mTransactionListenerOnCommitCalled, false);
        assertEquals(mTransactionListenerOnRollbackCalled, true);
    }

    private class TestSQLiteTransactionListener implements SQLiteTransactionListener {
        public void onBegin() {
            mTransactionListenerOnBeginCalled = true;
        }

        public void onCommit() {
            mTransactionListenerOnCommitCalled = true;
        }

        public void onRollback() {
            mTransactionListenerOnRollbackCalled = true;
        }
    }

    public void testGroupConcat() {
        mDatabase.execSQL("CREATE TABLE test (i INT, j TEXT);");

        // insert 2 rows
        String sql = "INSERT INTO test (i) VALUES (?);";
        SQLiteStatement insertStatement = mDatabase.compileStatement(sql);
        DatabaseUtils.bindObjectToProgram(insertStatement, 1, 1);
        insertStatement.execute();
        DatabaseUtils.bindObjectToProgram(insertStatement, 1, 2);
        insertStatement.execute();
        insertStatement.close();

        // make sure there are 2 rows in the table
        Cursor cursor = mDatabase.rawQuery("SELECT count(*) FROM test", null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToNext();
        assertEquals(2, cursor.getInt(0));
        cursor.close();

        // concatenate column j from all the rows. should return NULL
        cursor = mDatabase.rawQuery("SELECT group_concat(j, ' ') FROM test", null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToNext();
        assertNull(cursor.getString(0));
        cursor.close();

        // drop the table
        mDatabase.execSQL("DROP TABLE test;");
        // should get no exceptions
    }

    public void testSchemaChanges() {
        mDatabase.execSQL("CREATE TABLE test (i INT, j INT);");

        // at the beginning, there is no record in the database.
        Cursor cursor = mDatabase.rawQuery("SELECT * FROM test", null);
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());
        cursor.close();

        String sql = "INSERT INTO test VALUES (?, ?);";
        SQLiteStatement insertStatement = mDatabase.compileStatement(sql);
        DatabaseUtils.bindObjectToProgram(insertStatement, 1, 1);
        DatabaseUtils.bindObjectToProgram(insertStatement, 2, 2);
        insertStatement.execute();
        insertStatement.close();

        // read the data from the table and make sure it is correct
        cursor = mDatabase.rawQuery("SELECT i,j FROM test", null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToNext();
        assertEquals(1, cursor.getInt(0));
        assertEquals(2, cursor.getInt(1));
        cursor.close();

        // alter the table and execute another statement
        mDatabase.execSQL("ALTER TABLE test ADD COLUMN k int;");
        sql = "INSERT INTO test VALUES (?, ?, ?);";
        insertStatement = mDatabase.compileStatement(sql);
        DatabaseUtils.bindObjectToProgram(insertStatement, 1, 3);
        DatabaseUtils.bindObjectToProgram(insertStatement, 2, 4);
        DatabaseUtils.bindObjectToProgram(insertStatement, 3, 5);
        insertStatement.execute();
        insertStatement.close();

        // read the data from the table and make sure it is correct
        cursor = mDatabase.rawQuery("SELECT i,j,k FROM test", null);
        assertNotNull(cursor);
        assertEquals(2, cursor.getCount());
        cursor.moveToNext();
        assertEquals(1, cursor.getInt(0));
        assertEquals(2, cursor.getInt(1));
        assertNull(cursor.getString(2));
        cursor.moveToNext();
        assertEquals(3, cursor.getInt(0));
        assertEquals(4, cursor.getInt(1));
        assertEquals(5, cursor.getInt(2));
        cursor.close();

        // make sure the old statement - which should *try to reuse* cached query plan -
        // still works
        cursor = mDatabase.rawQuery("SELECT i,j FROM test", null);
        assertNotNull(cursor);
        assertEquals(2, cursor.getCount());
        cursor.moveToNext();
        assertEquals(1, cursor.getInt(0));
        assertEquals(2, cursor.getInt(1));
        cursor.moveToNext();
        assertEquals(3, cursor.getInt(0));
        assertEquals(4, cursor.getInt(1));
        cursor.close();

        SQLiteStatement deleteStatement = mDatabase.compileStatement("DELETE FROM test");
        deleteStatement.execute();
        deleteStatement.close();
    }

    public void testSchemaChangesNewTable() {
        mDatabase.execSQL("CREATE TABLE test (i INT, j INT);");

        // at the beginning, there is no record in the database.
        Cursor cursor = mDatabase.rawQuery("SELECT * FROM test", null);
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());
        cursor.close();

        String sql = "INSERT INTO test VALUES (?, ?);";
        SQLiteStatement insertStatement = mDatabase.compileStatement(sql);
        DatabaseUtils.bindObjectToProgram(insertStatement, 1, 1);
        DatabaseUtils.bindObjectToProgram(insertStatement, 2, 2);
        insertStatement.execute();
        insertStatement.close();

        // read the data from the table and make sure it is correct
        cursor = mDatabase.rawQuery("SELECT i,j FROM test", null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToNext();
        assertEquals(1, cursor.getInt(0));
        assertEquals(2, cursor.getInt(1));
        cursor.close();

        // alter the table and execute another statement
        mDatabase.execSQL("CREATE TABLE test_new (i INT, j INT, k INT);");
        sql = "INSERT INTO test_new VALUES (?, ?, ?);";
        insertStatement = mDatabase.compileStatement(sql);
        DatabaseUtils.bindObjectToProgram(insertStatement, 1, 3);
        DatabaseUtils.bindObjectToProgram(insertStatement, 2, 4);
        DatabaseUtils.bindObjectToProgram(insertStatement, 3, 5);
        insertStatement.execute();
        insertStatement.close();

        // read the data from the table and make sure it is correct
        cursor = mDatabase.rawQuery("SELECT i,j,k FROM test_new", null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToNext();
        assertEquals(3, cursor.getInt(0));
        assertEquals(4, cursor.getInt(1));
        assertEquals(5, cursor.getInt(2));
        cursor.close();

        // make sure the old statement - which should *try to reuse* cached query plan -
        // still works
        cursor = mDatabase.rawQuery("SELECT i,j FROM test", null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToNext();
        assertEquals(1, cursor.getInt(0));
        assertEquals(2, cursor.getInt(1));
        cursor.close();

        SQLiteStatement deleteStatement = mDatabase.compileStatement("DELETE FROM test");
        deleteStatement.execute();
        deleteStatement.close();

        SQLiteStatement deleteStatement2 = mDatabase.compileStatement("DELETE FROM test_new");
        deleteStatement2.execute();
        deleteStatement2.close();
    }

    public void testSchemaChangesDropTable() {
        mDatabase.execSQL("CREATE TABLE test (i INT, j INT);");

        // at the beginning, there is no record in the database.
        Cursor cursor = mDatabase.rawQuery("SELECT * FROM test", null);
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());
        cursor.close();

        String sql = "INSERT INTO test VALUES (?, ?);";
        SQLiteStatement insertStatement = mDatabase.compileStatement(sql);
        DatabaseUtils.bindObjectToProgram(insertStatement, 1, 1);
        DatabaseUtils.bindObjectToProgram(insertStatement, 2, 2);
        insertStatement.execute();
        insertStatement.close();

        // read the data from the table and make sure it is correct
        cursor = mDatabase.rawQuery("SELECT i,j FROM test", null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToNext();
        assertEquals(1, cursor.getInt(0));
        assertEquals(2, cursor.getInt(1));
    }

    /**
     * With sqlite's write-ahead-logging (WAL) enabled, readers get old version of data
     * from the table that a writer is modifying at the same time.
     * <p>
     * This method does the following to test this sqlite3 feature
     * <ol>
     *   <li>creates a table in the database and populates it with 5 rows of data</li>
     *   <li>do "select count(*) from this_table" and expect to receive 5</li>
     *   <li>start a writer thread who BEGINs a transaction, INSERTs a single row
     *   into this_table</li>
     *   <li>writer stops the transaction at this point, kicks off a reader thread - which will
     *       do  the above SELECT query: "select count(*) from this_table"</li>
     *   <li>this query should return value 5 - because writer is still in transaction and
     *    sqlite returns OLD version of the data</li>
     *   <li>writer ends the transaction, thus making the extra row now visible to everyone</li>
     *   <li>reader is kicked off again to do the same query. this time query should
     *   return value = 6 which includes the newly inserted row into this_table.</li>
     *</p>
     * @throws InterruptedException
     */
    @LargeTest
    public void testReaderGetsOldVersionOfDataWhenWriterIsInXact() throws InterruptedException {
        // redo setup to create WAL enabled database
        mDatabase.close();
        new File(mDatabase.getPath()).delete();
        mDatabase = SQLiteDatabase.openOrCreateDatabase(mDatabaseFile.getPath(), null, null);
        boolean rslt = mDatabase.enableWriteAheadLogging();
        assertTrue(rslt);
        assertNotNull(mDatabase);

        // create a new table and insert 5 records into it.
        mDatabase.execSQL("CREATE TABLE t1 (i int, j int);");
        mDatabase.beginTransaction();
        for (int i = 0; i < 5; i++) {
            mDatabase.execSQL("insert into t1 values(?,?);", new String[] {i+"", i+""});
        }
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();

        // make sure a reader can read the above data
        ReaderQueryingData r1 = new ReaderQueryingData(5);
        r1.start();
        Thread.yield();
        try {r1.join();} catch (Exception e) {}

        WriterDoingSingleTransaction w = new WriterDoingSingleTransaction();
        w.start();
        w.join();
    }

    private class WriterDoingSingleTransaction extends Thread {
        @Override public void run() {
            // start a transaction
            mDatabase.beginTransactionNonExclusive();
            mDatabase.execSQL("insert into t1 values(?,?);", new String[] {"11", "11"});
            assertTrue(mDatabase.isOpen());

            // while the writer is in a transaction, start a reader and make sure it can still
            // read 5 rows of data (= old data prior to the current transaction)
            ReaderQueryingData r1 = new ReaderQueryingData(5);
            r1.start();
            try {r1.join();} catch (Exception e) {}

            // now, have the writer do the select count(*)
            // it should execute on the same connection as this transaction
            // and count(*) should reflect the newly inserted row
            Long l = DatabaseUtils.longForQuery(mDatabase, "select count(*) from t1", null);
            assertEquals(6, l.intValue());

            // end transaction
            mDatabase.setTransactionSuccessful();
            mDatabase.endTransaction();

            // reader should now be able to read 6 rows = new data AFTER this transaction
            r1 = new ReaderQueryingData(6);
            r1.start();
            try {r1.join();} catch (Exception e) {}
        }
    }

    private class ReaderQueryingData extends Thread {
        private int count;
        /**
         * constructor with a param to indicate the number of rows expected to be read
         */
        public ReaderQueryingData(int count) {
            this.count = count;
        }
        @Override public void run() {
            Long l = DatabaseUtils.longForQuery(mDatabase, "select count(*) from t1", null);
            assertEquals(count, l.intValue());
        }
    }

    public void testExceptionsFromEnableWriteAheadLogging() {
        // attach a database
        // redo setup to create WAL enabled database
        mDatabase.close();
        new File(mDatabase.getPath()).delete();
        mDatabase = SQLiteDatabase.openOrCreateDatabase(mDatabaseFile.getPath(), null, null);

        // attach a database and call enableWriteAheadLogging - should not be allowed
        mDatabase.execSQL("attach database ':memory:' as memoryDb");
        assertFalse(mDatabase.isWriteAheadLoggingEnabled());
        assertFalse(mDatabase.enableWriteAheadLogging());
        assertFalse(mDatabase.isWriteAheadLoggingEnabled());

        // enableWriteAheadLogging on memory database is not allowed
        SQLiteDatabase db = SQLiteDatabase.create(null);
        assertFalse(mDatabase.isWriteAheadLoggingEnabled());
        assertFalse(db.enableWriteAheadLogging());
        assertFalse(mDatabase.isWriteAheadLoggingEnabled());
        db.close();
    }

    public void testEnableThenDisableWriteAheadLogging() {
        // Enable WAL.
        assertFalse(mDatabase.isWriteAheadLoggingEnabled());
        assertTrue(mDatabase.enableWriteAheadLogging());
        assertTrue(mDatabase.isWriteAheadLoggingEnabled());
        assertTrue(DatabaseUtils.stringForQuery(mDatabase, "PRAGMA journal_mode", null)
                .equalsIgnoreCase("WAL"));

        // Enabling when already enabled should have no observable effect.
        assertTrue(mDatabase.enableWriteAheadLogging());
        assertTrue(mDatabase.isWriteAheadLoggingEnabled());
        assertTrue(DatabaseUtils.stringForQuery(mDatabase, "PRAGMA journal_mode", null)
                .equalsIgnoreCase("WAL"));

        // Disabling when there are no connections should work.
        mDatabase.disableWriteAheadLogging();
        assertFalse(mDatabase.isWriteAheadLoggingEnabled());
    }

    public void testEnableThenDisableWriteAheadLoggingUsingOpenFlag() {
        new File(mDatabase.getPath()).delete();
        mDatabase = SQLiteDatabase.openDatabase(mDatabaseFile.getPath(), null,
                SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
                null);
        assertTrue(mDatabase.isWriteAheadLoggingEnabled());
        assertTrue(DatabaseUtils.stringForQuery(mDatabase, "PRAGMA journal_mode", null)
                .equalsIgnoreCase("WAL"));

        // Enabling when already enabled should have no observable effect.
        assertTrue(mDatabase.enableWriteAheadLogging());
        assertTrue(mDatabase.isWriteAheadLoggingEnabled());
        assertTrue(DatabaseUtils.stringForQuery(mDatabase, "PRAGMA journal_mode", null)
                .equalsIgnoreCase("WAL"));

        // Disabling when there are no connections should work.
        mDatabase.disableWriteAheadLogging();
        assertFalse(mDatabase.isWriteAheadLoggingEnabled());
    }

    public void testEnableWriteAheadLoggingFromContextUsingModeFlag() {
        // Without the MODE_ENABLE_WRITE_AHEAD_LOGGING flag, database opens without WAL.
        getContext().deleteDatabase(DATABASE_FILE_NAME);

        File f = getContext().getDatabasePath(DATABASE_FILE_NAME);
        mDatabase = SQLiteDatabase.openOrCreateDatabase(f,null);
        assertFalse(mDatabase.isWriteAheadLoggingEnabled());
        mDatabase.close();

        // // With the MODE_ENABLE_WRITE_AHEAD_LOGGING flag, database opens with WAL.
        // getContext().deleteDatabase(DATABASE_FILE_NAME);
        // mDatabase = getContext().openOrCreateDatabase(DATABASE_FILE_NAME,
        //        Context.MODE_PRIVATE | Context.MODE_ENABLE_WRITE_AHEAD_LOGGING, null);
        // assertTrue(mDatabase.isWriteAheadLoggingEnabled());
        // mDatabase.close();
    }

    public void testEnableWriteAheadLoggingShouldThrowIfTransactionInProgress() {
        assertFalse(mDatabase.isWriteAheadLoggingEnabled());
        String oldJournalMode = DatabaseUtils.stringForQuery(
                mDatabase, "PRAGMA journal_mode", null);

        // Begin transaction.
        mDatabase.beginTransaction();

        try {
            // Attempt to enable WAL should fail.
            mDatabase.enableWriteAheadLogging();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException ex) {
            // expected
        }

        assertFalse(mDatabase.isWriteAheadLoggingEnabled());
        assertTrue(DatabaseUtils.stringForQuery(mDatabase, "PRAGMA journal_mode", null)
                .equalsIgnoreCase(oldJournalMode));
    }

    public void testDisableWriteAheadLoggingShouldThrowIfTransactionInProgress() {
        // Enable WAL.
        assertFalse(mDatabase.isWriteAheadLoggingEnabled());
        assertTrue(mDatabase.enableWriteAheadLogging());
        assertTrue(mDatabase.isWriteAheadLoggingEnabled());

        // Begin transaction.
        mDatabase.beginTransaction();

        try {
            // Attempt to disable WAL should fail.
            mDatabase.disableWriteAheadLogging();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException ex) {
            // expected
        }

        assertTrue(mDatabase.isWriteAheadLoggingEnabled());
        assertTrue(DatabaseUtils.stringForQuery(mDatabase, "PRAGMA journal_mode", null)
                .equalsIgnoreCase("WAL"));
    }

    public void testEnableAndDisableForeignKeys() {
        // Initially off.
        assertEquals(0, DatabaseUtils.longForQuery(mDatabase, "PRAGMA foreign_keys", null));

        // Enable foreign keys.
        mDatabase.setForeignKeyConstraintsEnabled(true);
        assertEquals(1, DatabaseUtils.longForQuery(mDatabase, "PRAGMA foreign_keys", null));

        // Disable foreign keys.
        mDatabase.setForeignKeyConstraintsEnabled(false);
        assertEquals(0, DatabaseUtils.longForQuery(mDatabase, "PRAGMA foreign_keys", null));

        // Cannot configure foreign keys if there are transactions in progress.
        mDatabase.beginTransaction();
        try {
            mDatabase.setForeignKeyConstraintsEnabled(true);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException ex) {
            // expected
        }
        assertEquals(0, DatabaseUtils.longForQuery(mDatabase, "PRAGMA foreign_keys", null));
        mDatabase.endTransaction();

        // Enable foreign keys should work again after transaction complete.
        mDatabase.setForeignKeyConstraintsEnabled(true);
        assertEquals(1, DatabaseUtils.longForQuery(mDatabase, "PRAGMA foreign_keys", null));
    }
}
