package net.zetetic.database.sqlcipher;

import android.database.Cursor;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import org.junit.Test;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class NativeCommitCancellationTest extends AndroidSQLCipherTestCase {
    @Test public void interruptInsideNativeCommit() throws Exception {
        database.execSQL("CREATE TABLE item(id INTEGER)");
        database.beginTransaction();
        database.execSQL("INSERT INTO item VALUES (1)");
        database.setTransactionSuccessful();
        CancellationSignal signal = new CancellationSignal();
        NativeCommitProbe.arm();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> canceller = executor.submit(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (NativeCommitProbe.phase() != 2 && System.nanoTime() < deadline) Thread.yield();
            if (NativeCommitProbe.phase() != 2) throw new AssertionError("SQLite COMMIT progress callback not reached");
            signal.cancel();
        });
        try {
            try {
                database.getThreadSession().endTransaction(signal);
                fail("Expected cancellation from inside sqlite3_step(COMMIT)");
            } catch (OperationCanceledException expected) {
                assertFalse(database.inTransaction());
            }
            canceller.get(10, TimeUnit.SECONDS);
            assertEquals("Native progress callback observed cancellation", 3, NativeCommitProbe.phase());
            try (Cursor c = database.rawQuery("SELECT count(*) FROM item", (String[]) null)) {
                assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0));
            }
            database.beginTransaction();
            database.execSQL("INSERT INTO item VALUES (2)");
            database.setTransactionSuccessful();
            database.endTransaction();
            try (Cursor c = database.rawQuery("SELECT count(*) FROM item", (String[]) null)) {
                assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0));
            }
        } finally { executor.shutdownNow(); }
    }
}
