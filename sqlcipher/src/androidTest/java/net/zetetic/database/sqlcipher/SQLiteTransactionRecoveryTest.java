package net.zetetic.database.sqlcipher;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;

import org.junit.Test;

import static org.junit.Assert.*;

public class SQLiteTransactionRecoveryTest extends AndroidSQLCipherTestCase {
    private void setupDeferredConstraint(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
        db.execSQL("CREATE TABLE parent(id INTEGER PRIMARY KEY)");
        db.execSQL("CREATE TABLE child(id INTEGER REFERENCES parent(id) DEFERRABLE INITIALLY DEFERRED)");
    }

    private int count(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT count(*) FROM " + table, (String[]) null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    @Test public void failedCommitRollsBackBeforeConnectionReuse() throws Exception {
        setupDeferredConstraint(database);
        database.beginTransaction();
        database.execSQL("INSERT INTO child VALUES (1)");
        database.setTransactionSuccessful();
        try {
            database.endTransaction();
            fail("COMMIT should reject the deferred foreign key");
        } catch (SQLiteConstraintException expected) {
            assertFalse(database.inTransaction());
        }
        assertEquals(0, count(database, "child"));
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            assertEquals(0, (int) worker.submit(() -> count(database, "child"))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            worker.shutdownNow();
        }
        database.beginTransaction();
        database.execSQL("INSERT INTO parent VALUES (1)");
        database.setTransactionSuccessful();
        database.endTransaction();
        assertEquals(1, count(database, "parent"));
    }

    @Test public void cancelledBeforeEndKeepsManagedTransactionForCleanup() {
        database.execSQL("CREATE TABLE item(id INTEGER)");
        database.beginTransaction();
        database.execSQL("INSERT INTO item VALUES (1)");
        CancellationSignal signal = new CancellationSignal();
        signal.cancel();
        try {
            database.getThreadSession().endTransaction(signal);
            fail("Expected cancellation");
        } catch (OperationCanceledException expected) {
            assertTrue(database.inTransaction());
        }
        database.endTransaction();
        assertEquals(0, count(database, "item"));
    }

    @Test public void cancellationAfterEndStartsDoesNotLeaveConnectionInTransaction() {
        database.execSQL("CREATE TABLE item(id INTEGER)");
        CancellationSignal signal = new CancellationSignal();
        database.beginTransactionWithListener(new android.database.sqlite.SQLiteTransactionListener() {
            @Override public void onBegin() { }
            @Override public void onCommit() { signal.cancel(); }
            @Override public void onRollback() { }
        });
        database.execSQL("INSERT INTO item VALUES (1)");
        database.setTransactionSuccessful();
        try {
            database.getThreadSession().endTransaction(signal);
            fail("Expected cancellation while ending transaction");
        } catch (OperationCanceledException expected) {
            assertFalse(database.inTransaction());
        }
        assertEquals(0, count(database, "item"));
        database.beginTransaction();
        database.execSQL("INSERT INTO item VALUES (2)");
        database.setTransactionSuccessful();
        database.endTransaction();
        assertEquals(1, count(database, "item"));
    }

    @Test public void cancelledRollbackIsRetriedWithoutCancellingInMemoryDatabase() {
        try (SQLiteDatabase memory = SQLiteDatabase.create(null)) {
            memory.execSQL("CREATE TABLE item(id INTEGER)");
            memory.execSQL("INSERT INTO item VALUES (1)");
            CancellationSignal signal = new CancellationSignal();
            memory.beginTransactionWithListener(new android.database.sqlite.SQLiteTransactionListener() {
                @Override public void onBegin() { }
                @Override public void onCommit() { }
                @Override public void onRollback() { signal.cancel(); }
            });
            memory.execSQL("INSERT INTO item VALUES (2)");
            try {
                memory.getThreadSession().endTransaction(signal);
                fail("Expected cancellation of the original rollback");
            } catch (OperationCanceledException expected) {
                assertFalse(memory.inTransaction());
            }
            assertEquals("The committed row survives; the cancelled transaction is rolled back", 1,
                    count(memory, "item"));
            memory.beginTransaction();
            memory.execSQL("INSERT INTO item VALUES (3)");
            memory.setTransactionSuccessful();
            memory.endTransaction();
            assertEquals(2, count(memory, "item"));
        }
    }

    @Test public void reentrantOutermostEndIsRejectedUntilSelectFinishes() {
        verifyReentrantOutermostEnd(false);
    }

    @Test public void reentrantOutermostEndIsRejectedUntilTriggerFinishes() {
        verifyReentrantOutermostEnd(true);
    }

    private void verifyReentrantOutermostEnd(boolean useTrigger) {
        database.execSQL("CREATE TABLE item(id INTEGER)");
        java.util.concurrent.atomic.AtomicBoolean rejected = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean stillInTransaction = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicInteger observed = new java.util.concurrent.atomic.AtomicInteger(-1);
        database.addCustomFunction("probe_end", 0, args -> {
            try {
                database.endTransaction();
            } catch (IllegalStateException expected) {
                rejected.set(true);
            }
            stillInTransaction.set(database.inTransaction());
            observed.set(count(database, "item"));
        });
        if (useTrigger) {
            database.execSQL("CREATE TRIGGER probe_trigger AFTER INSERT ON item BEGIN SELECT probe_end(); END");
        }
        database.beginTransaction();
        if (useTrigger) {
            database.execSQL("INSERT INTO item VALUES (1)");
        } else {
            database.execSQL("INSERT INTO item VALUES (1)");
            try (Cursor cursor = database.rawQuery("SELECT probe_end()", (String[]) null)) {
                assertTrue(cursor.moveToFirst());
            }
        }
        assertTrue("Outermost transaction must remain owned by the outer statement", rejected.get());
        assertTrue(stillInTransaction.get());
        assertEquals(1, observed.get());
        database.setTransactionSuccessful();
        database.endTransaction();
        assertEquals(1, count(database, "item"));
    }

    @Test public void rejectedReentrantSqlCommitDoesNotMarkTransactionSuccessful() {
        database.execSQL("CREATE TABLE item(id INTEGER)");
        java.util.concurrent.atomic.AtomicBoolean rejected = new java.util.concurrent.atomic.AtomicBoolean();
        database.addCustomFunction("try_commit", 0, args -> {
            try { database.execSQL("COMMIT"); }
            catch (IllegalStateException expected) { rejected.set(true); }
        });
        database.beginTransaction();
        database.execSQL("INSERT INTO item VALUES (1)");
        try (Cursor cursor = database.rawQuery("SELECT try_commit()", (String[]) null)) {
            assertTrue(cursor.moveToFirst());
        }
        assertTrue(rejected.get());
        database.endTransaction();
        assertEquals("A rejected SQL COMMIT must not mark the transaction successful", 0,
                count(database, "item"));
    }

    @Test public void reentrantNestedTransactionCanStillComplete() {
        database.execSQL("CREATE TABLE item(id INTEGER)");
        java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean();
        database.addCustomFunction("nested_write", 0, args -> {
            database.beginTransaction();
            try {
                database.execSQL("INSERT INTO item VALUES (1)");
                database.setTransactionSuccessful();
            } finally { database.endTransaction(); }
            completed.set(true);
        });
        database.beginTransaction();
        try (Cursor cursor = database.rawQuery("SELECT nested_write()", (String[]) null)) {
            assertTrue(cursor.moveToFirst());
        }
        assertTrue("Nested managed transaction should remain supported", completed.get());
        database.setTransactionSuccessful();
        database.endTransaction();
        assertEquals(1, count(database, "item"));
    }

    @Test public void automaticallyRolledBackFileTransactionKeepsCommittedData() {
        database.execSQL("CREATE TABLE item(id INTEGER UNIQUE)");
        database.execSQL("INSERT INTO item VALUES (1)");
        database.beginTransaction();
        try {
            database.execSQL("INSERT OR ROLLBACK INTO item VALUES (1)");
            fail("Expected constraint failure");
        } catch (SQLiteConstraintException expected) {
            // SQLite has already rolled back the native transaction.
        }
        database.setTransactionSuccessful();
        try {
            database.endTransaction();
            fail("Expected COMMIT to fail after automatic rollback");
        } catch (RuntimeException expected) {
            assertFalse(database.inTransaction());
            assertEquals(0, expected.getSuppressed().length);
        }
        assertEquals(1, count(database, "item"));
    }

    @Test public void databaseFullDuringTransactionDoesNotLeakConnectionState() {
        database.execSQL("CREATE TABLE item(data BLOB)");
        int pages;
        try (Cursor cursor = database.rawQuery("PRAGMA page_count", (String[]) null)) {
            assertTrue(cursor.moveToFirst());
            pages = cursor.getInt(0);
        }
        try (Cursor cursor = database.rawQuery("PRAGMA max_page_count = " + pages, (String[]) null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(pages, cursor.getInt(0));
        }
        database.beginTransaction();
        try {
            database.execSQL("INSERT INTO item VALUES (zeroblob(4194304))");
            fail("Expected SQLite database-full failure");
        } catch (android.database.sqlite.SQLiteFullException expected) {
            // SQLite may automatically roll back on SQLITE_FULL.
        }
        try {
            database.endTransaction();
        } catch (RuntimeException alreadyRolledBack) {
            // The driver must discard the connection if native cleanup fails.
        }
        assertEquals(0, count(database, "item"));
    }

    @Test public void commitReportsJournalDeletionFailure() throws Exception {
        java.io.File dir = new java.io.File(context.getCacheDir(), "commit-io-" + java.util.UUID.randomUUID());
        assertTrue(dir.mkdir());
        java.io.File file = new java.io.File(dir, "test.db");
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, "foo", null, null, null);
        try {
            db.execSQL("CREATE TABLE item(id INTEGER)");
            db.beginTransaction();
            db.execSQL("INSERT INTO item VALUES (1)");
            db.setTransactionSuccessful();
            android.system.Os.chmod(dir.getAbsolutePath(), 0500);
            try {
                db.endTransaction();
                fail("Expected an I/O failure when COMMIT cannot delete its journal");
            } catch (android.database.sqlite.SQLiteDiskIOException expected) {
                assertFalse(db.inTransaction());
            } finally {
                android.system.Os.chmod(dir.getAbsolutePath(), 0700);
            }
            int observed = count(db, "item");
            try (SQLiteDatabase independent = SQLiteDatabase.openOrCreateDatabase(file, "foo", null, null, null)) {
                assertEquals("pooled connection must agree with an independent connection", observed,
                        count(independent, "item"));
            }
            db.beginTransaction();
            db.execSQL("INSERT INTO item VALUES (2)");
            db.setTransactionSuccessful();
            db.endTransaction();
            try (SQLiteDatabase independent = SQLiteDatabase.openOrCreateDatabase(file, "foo", null, null, null)) {
                assertEquals(observed + 1, count(independent, "item"));
            }
        } finally {
            db.close();
            android.system.Os.chmod(dir.getAbsolutePath(), 0700);
            for (java.io.File child : dir.listFiles()) child.delete();
            dir.delete();
        }
    }

    @Test public void cancellationAfterWaiterIsDequeuedDoesNotSearchMissingQueue() throws Exception {
        database.beginTransaction();
        SQLiteSession session = database.getThreadSession();
        java.lang.reflect.Field connectionField = SQLiteSession.class.getDeclaredField("mConnection");
        java.lang.reflect.Field poolField = SQLiteSession.class.getDeclaredField("mConnectionPool");
        connectionField.setAccessible(true); poolField.setAccessible(true);
        SQLiteConnectionPool pool = (SQLiteConnectionPool) poolField.get(session);
        SQLiteConnection connection = (SQLiteConnection) connectionField.get(session);
        java.lang.reflect.Field lockField = SQLiteConnectionPool.class.getDeclaredField("mLock");
        java.lang.reflect.Field queueField = SQLiteConnectionPool.class.getDeclaredField("mConnectionWaiterQueue");
        lockField.setAccessible(true); queueField.setAccessible(true);
        Object lock = lockField.get(pool);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<Boolean> writer = executor.submit(() -> {
            try { database.beginTransaction(); database.endTransaction(); return false; }
            catch (IllegalStateException expected) { return true; }
        });
        try {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            Object waiter = null;
            while (System.nanoTime() < deadline) {
                synchronized (lock) { waiter = queueField.get(pool); }
                if (waiter != null) break;
                Thread.yield();
            }
            assertNotNull("Writer should be queued", waiter);
            java.lang.reflect.Method cancel = SQLiteConnectionPool.class.getDeclaredMethod(
                    "cancelConnectionWaiterLocked", waiter.getClass());
            cancel.setAccessible(true);
            synchronized (lock) {
                pool.discardConnection(connection);
                // Models a cancellation listener installed just after invalidation dequeued this waiter.
                cancel.invoke(pool, waiter);
            }
            assertTrue(writer.get(10, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            database.endTransaction();
            executor.shutdownNow();
        }
    }

    @Test public void assignedWaiterClosesConnectionIfPoolBecomesUnusable() throws Exception {
        assertTrue(database.enableWriteAheadLogging());
        java.lang.reflect.Field databasePool = SQLiteDatabase.class.getDeclaredField("mConnectionPoolLocked");
        databasePool.setAccessible(true);
        SQLiteConnectionPool pool = (SQLiteConnectionPool) databasePool.get(database);
        java.lang.reflect.Field lockField = SQLiteConnectionPool.class.getDeclaredField("mLock");
        java.lang.reflect.Field queueField = SQLiteConnectionPool.class.getDeclaredField("mConnectionWaiterQueue");
        java.lang.reflect.Field maxField = SQLiteConnectionPool.class.getDeclaredField("mMaxConnectionPoolSize");
        java.lang.reflect.Field acquiredField = SQLiteConnectionPool.class.getDeclaredField("mAcquiredConnections");
        lockField.setAccessible(true); queueField.setAccessible(true); maxField.setAccessible(true); acquiredField.setAccessible(true);
        Object lock = lockField.get(pool);
        synchronized (lock) { maxField.setInt(pool, 2); }
        SQLiteConnection primary = pool.acquireConnection(null, SQLiteConnectionPool.CONNECTION_FLAG_PRIMARY_CONNECTION_AFFINITY, null);
        SQLiteConnection reader = pool.acquireConnection(null, SQLiteConnectionPool.CONNECTION_FLAG_READ_ONLY, null);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<Boolean> waiting = executor.submit(() -> {
            try {
                SQLiteConnection unexpected = pool.acquireConnection(null, SQLiteConnectionPool.CONNECTION_FLAG_READ_ONLY, null);
                pool.releaseConnection(unexpected);
                return false;
            } catch (IllegalStateException expected) { return true; }
        });
        try {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            Object waiter = null;
            while (System.nanoTime() < deadline) {
                synchronized (lock) { waiter = queueField.get(pool); }
                if (waiter != null) break;
                Thread.yield();
            }
            assertNotNull("Reader should be queued", waiter);
            java.lang.reflect.Field assigned = waiter.getClass().getDeclaredField("mAssignedConnection");
            assigned.setAccessible(true);
            synchronized (lock) {
                pool.releaseConnection(reader);
                assertSame("Released reader must be assigned while the lock is held", reader, assigned.get(waiter));
                pool.discardConnection(primary);
            }
            assertTrue(waiting.get(10, java.util.concurrent.TimeUnit.SECONDS));
            synchronized (lock) {
                assertFalse("Assigned connection must be returned or closed", ((java.util.Map<?, ?>) acquiredField.get(pool)).containsKey(reader));
            }
        } finally {
            pool.releaseConnection(primary);
            executor.shutdownNow();
        }
    }

    @Test public void queuedWriterFailsClosedBeforeUntrustedConnectionIsReleased() throws Exception {
        database.execSQL("CREATE TABLE item(id INTEGER)");
        database.execSQL("INSERT INTO item VALUES (1)");
        database.beginTransactionNonExclusive();
        database.execSQL("INSERT INTO item VALUES (2)");
        SQLiteSession session = database.getThreadSession();
        java.lang.reflect.Field connectionField = SQLiteSession.class.getDeclaredField("mConnection");
        java.lang.reflect.Field poolField = SQLiteSession.class.getDeclaredField("mConnectionPool");
        connectionField.setAccessible(true);
        poolField.setAccessible(true);
        SQLiteConnectionPool pool = (SQLiteConnectionPool) poolField.get(session);
        SQLiteConnection connection = (SQLiteConnection) connectionField.get(session);
        java.lang.reflect.Field lockField = SQLiteConnectionPool.class.getDeclaredField("mLock");
        java.lang.reflect.Field queueField = SQLiteConnectionPool.class.getDeclaredField("mConnectionWaiterQueue");
        lockField.setAccessible(true);
        queueField.setAccessible(true);
        Object lock = lockField.get(pool);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<Boolean> writer = executor.submit(() -> {
            try {
                database.beginTransactionNonExclusive();
                try {
                    database.execSQL("INSERT INTO item VALUES (3)");
                    database.setTransactionSuccessful();
                } finally { database.endTransaction(); }
                return false;
            } catch (IllegalStateException expected) {
                return true;
            }
        });
        try {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            boolean queued = false;
            while (System.nanoTime() < deadline) {
                synchronized (lock) { queued = queueField.get(pool) != null; }
                if (queued) break;
                Thread.yield();
            }
            assertTrue("The writer must be waiting in the real connection pool", queued);
            pool.discardConnection(connection);
            assertTrue("A waiting writer must fail rather than receive the untrusted connection",
                    writer.get(10, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            database.endTransaction();
            executor.shutdownNow();
        }
        database.close();
        database = SQLiteDatabase.openOrCreateDatabase(databaseFilePath, "foo", null, null, null);
        assertEquals("Only the previously committed row remains", 1, count(database, "item"));
    }

    @Test public void discardedFileConnectionRequiresExplicitReopen() throws Exception {
        database.execSQL("CREATE TABLE item(id INTEGER)");
        database.execSQL("INSERT INTO item VALUES (1)");
        discardOwnedConnection(database);
        try {
            count(database, "item");
            fail("The pool must fail closed after a connection becomes untrusted");
        } catch (IllegalStateException expected) { }
        database.close();
        database = SQLiteDatabase.openOrCreateDatabase(databaseFilePath, "foo", null, null, null);
        assertEquals(1, count(database, "item"));
        database.execSQL("INSERT INTO item VALUES (2)");
        assertEquals(2, count(database, "item"));
    }

    @Test public void discardedFileConnectionWithAttachedDatabaseFailsClosed() throws Exception {
        database.execSQL("ATTACH DATABASE ':memory:' AS scratch");
        database.execSQL("CREATE TABLE scratch.item(id INTEGER)");
        database.execSQL("INSERT INTO scratch.item VALUES (1)");
        discardOwnedConnection(database);
        try {
            database.rawQuery("SELECT count(*) FROM scratch.item", (String[]) null).close();
            fail("A discarded connection with attached state must not silently reopen");
        } catch (IllegalStateException expected) { }
    }

    @Test public void discardedFileConnectionWithTemporaryTableFailsClosed() throws Exception {
        database.execSQL("CREATE TEMP TABLE scratch_item(id INTEGER)");
        database.execSQL("INSERT INTO scratch_item VALUES (1)");
        discardOwnedConnection(database);
        try {
            database.rawQuery("SELECT count(*) FROM scratch_item", (String[]) null).close();
            fail("A discarded connection with temporary state must not silently reopen");
        } catch (IllegalStateException expected) { }
    }

    private void discardOwnedConnection(SQLiteDatabase db) throws Exception {
        db.beginTransaction();
        SQLiteSession session = db.getThreadSession();
        java.lang.reflect.Field connectionField = SQLiteSession.class.getDeclaredField("mConnection");
        java.lang.reflect.Field poolField = SQLiteSession.class.getDeclaredField("mConnectionPool");
        connectionField.setAccessible(true);
        poolField.setAccessible(true);
        ((SQLiteConnectionPool) poolField.get(session)).discardConnection(
                (SQLiteConnection) connectionField.get(session));
        db.endTransaction();
    }

    @Test public void discardedNamedMemoryUriFailsClosed() throws Exception {
        verifyEphemeralDiscard("file:rt15704_" + java.util.UUID.randomUUID().toString().replace("-", "")
                + "?mode=memory&cache=shared");
    }

    @Test public void discardedTemporaryDatabaseFailsClosed() throws Exception {
        verifyEphemeralDiscard("");
    }

    @Test public void discardedMemoryUriFailsClosed() throws Exception {
        verifyEphemeralDiscard("file::memory:");
    }

    private void verifyEphemeralDiscard(String path) throws Exception {
        try (SQLiteDatabase ephemeral = SQLiteDatabase.openDatabase(path, "foo", null,
                SQLiteDatabase.CREATE_IF_NECESSARY, null)) {
            ephemeral.execSQL("CREATE TABLE item(id INTEGER)");
            ephemeral.execSQL("INSERT INTO item VALUES (1)");
            discardOwnedConnection(ephemeral);
            try {
                count(ephemeral, "item");
                fail("Discarding an ephemeral database must not reopen it as an empty database");
            } catch (IllegalStateException expected) { }
        }
    }

    @Test public void automaticallyRolledBackInMemoryTransactionPreservesCommittedData() {
        verifyInMemoryAutomaticRollback(false);
    }

    @Test public void automaticallyRolledBackInMemoryCommitPreservesCommittedData() {
        verifyInMemoryAutomaticRollback(true);
    }

    private void verifyInMemoryAutomaticRollback(boolean markSuccessful) {
        try (SQLiteDatabase memory = SQLiteDatabase.create(null)) {
            memory.execSQL("CREATE TABLE item(id INTEGER UNIQUE)");
            memory.execSQL("INSERT INTO item VALUES (1)");
            memory.beginTransaction();
            try {
                memory.execSQL("INSERT OR ROLLBACK INTO item VALUES (1)");
                fail("Expected constraint failure");
            } catch (SQLiteConstraintException expected) { }
            if (markSuccessful) memory.setTransactionSuccessful();
            try {
                memory.endTransaction();
                fail("Expected transaction-end failure after automatic native rollback");
            } catch (android.database.sqlite.SQLiteException expected) {
                assertFalse(memory.inTransaction());
            }
            assertEquals("Previously committed in-memory data must remain available", 1, count(memory, "item"));
            memory.beginTransaction();
            memory.execSQL("INSERT INTO item VALUES (2)");
            memory.setTransactionSuccessful();
            memory.endTransaction();
            assertEquals(2, count(memory, "item"));
        }
    }
}
