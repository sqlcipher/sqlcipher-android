package net.zetetic.database.sqlcipher.driver

import android.content.Context
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifying that SQLCipherConnection.prepare() dispatches transaction SQL
 * and the journal_mode pragma to the SQLiteSession-aware paths, and
 * everything else to the standard SQLCipherStatement.
 */
@RunWith(AndroidJUnit4::class)
class SQLCipherConnectionTests {

    private lateinit var dbFile: File
    private lateinit var database: SQLiteDatabase
    private lateinit var connection: SQLCipherConnection

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        dbFile = context.getDatabasePath("test.db").apply {
            parentFile?.mkdirs()
        }
        SQLiteDatabase.deleteDatabase(dbFile)
        database = SQLiteDatabase.openOrCreateDatabase(
            dbFile,
            "test-passphrase",
            null,
            null,
            null)
        connection = SQLCipherConnection(database)
        exec("CREATE TABLE IF NOT EXISTS t (id INTEGER PRIMARY KEY, v TEXT)")
    }

    @After
    fun tearDown() {
        connection.close()
        SQLiteDatabase.deleteDatabase(dbFile)
    }

    @Test
    fun shouldTrackSessionStateWhenExecutingRawBeginImmediate() {
        assertFalse(connection.inTransaction())
        exec("BEGIN IMMEDIATE TRANSACTION")
        assertTrue(connection.inTransaction())
        assertTrue(database.inTransaction())
        exec("END TRANSACTION")
        assertFalse(connection.inTransaction())
    }

    @Test
    fun shouldPersistChangesWhenCommitting() {
        exec("BEGIN IMMEDIATE TRANSACTION")
        exec("INSERT INTO t (id, v) VALUES (1, 'kept')")
        exec("COMMIT")
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM t"))
    }

    @Test
    fun shouldDiscardChangesWhenRollingBack() {
        exec("BEGIN IMMEDIATE TRANSACTION")
        exec("INSERT INTO t (id, v) VALUES (1, 'discarded')")
        exec("ROLLBACK")
        assertFalse(connection.inTransaction())
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM t"))
    }

    @Test
    fun shouldRecognizeBeginRegardlessOfCaseAndLeadingComments() {
        exec("-- Room may prepends comments\nbegin immediate transaction")
        assertTrue(connection.inTransaction())
        exec("commit")
        assertFalse(connection.inTransaction())
    }

    @Test
    fun shouldPassSavepointsThroughInsideTransaction() {
        exec("BEGIN IMMEDIATE TRANSACTION")
        exec("INSERT INTO t (id, v) VALUES (1, 'outer')")
        exec("SAVEPOINT sp1")
        exec("INSERT INTO t (id, v) VALUES (2, 'inner')")
        exec("ROLLBACK TRANSACTION TO SAVEPOINT sp1")
        exec("RELEASE SAVEPOINT sp1")
        assertTrue("outer transaction should still be open", connection.inTransaction())
        exec("COMMIT")
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM t"))
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM t WHERE v = 'outer'"))
        assertFalse(connection.inTransaction())
    }

    @Test
    fun shouldReturnNoRowsWhenSteppingTransactionStatement() {
        connection.prepare("BEGIN IMMEDIATE TRANSACTION").use { statement ->
            assertFalse("transaction statement should report SQLITE_DONE", statement.step())
        }
        exec("ROLLBACK")
    }

    @Test
    fun shouldEnableWriteAheadLoggingWhenSettingJournalModeWal() {
        assertFalse(database.isWriteAheadLoggingEnabled)
        connection.prepare("PRAGMA journal_mode = WAL").use { statement ->
            assertTrue(statement.step())
            assertEquals("wal", statement.getText(0).lowercase())
        }
        assertTrue(database.isWriteAheadLoggingEnabled)
    }

    @Test
    fun shouldDisableWriteAheadLoggingWhenSettingJournalModeDelete() {
        exec("PRAGMA journal_mode = WAL")
        assertTrue(database.isWriteAheadLoggingEnabled)
        exec("PRAGMA journal_mode = DELETE")
        assertFalse(database.isWriteAheadLoggingEnabled)
    }

    @Test
    fun shouldPreserveTruncateJournalModeAcrossPoolReconfiguration() {
        exec("PRAGMA journal_mode = TRUNCATE")
        connection.prepare("PRAGMA journal_mode").use { statement ->
            assertTrue(statement.step())
            assertEquals("truncate", statement.getText(0).lowercase())
        }
    }

    @Test
    fun shouldNotToggleWriteAheadLoggingWhenQueryingJournalMode() {
        val before = database.isWriteAheadLoggingEnabled
        connection.prepare("PRAGMA journal_mode").use { statement ->
            assertTrue(statement.step())
        }
        assertEquals(before, database.isWriteAheadLoggingEnabled)
    }

    @Test
    fun shouldRunOtherPragmasOnStandardStatement() {
        connection.prepare("PRAGMA user_version").use { statement ->
            assertTrue(statement.step())
            assertEquals(0L, statement.getLong(0))
        }
    }

    @Test
    fun shouldReportColumnTypesUsingSqliteDataConstants() {
        connection.prepare("SELECT NULL, 1, 1.5, 'text', x'00'").use { statement ->
            assertTrue(statement.step())
            assertEquals(SQLITE_DATA_NULL, statement.getColumnType(0))
            assertEquals(SQLITE_DATA_INTEGER, statement.getColumnType(1))
            assertEquals(SQLITE_DATA_FLOAT, statement.getColumnType(2))
            assertEquals(SQLITE_DATA_TEXT, statement.getColumnType(3))
            assertEquals(SQLITE_DATA_BLOB, statement.getColumnType(4))
        }
    }

    @Test
    fun shouldRoundTripDataThroughStandardStatement() {
        connection.prepare("INSERT INTO t (id, v) VALUES (?, ?)").use { statement ->
            statement.bindLong(1, 42L)
            statement.bindText(2, "hello")
            assertFalse(statement.step())
        }
        connection.prepare("SELECT v FROM t WHERE id = ?").use { statement ->
            statement.bindLong(1, 42L)
            assertTrue(statement.step())
            assertEquals("hello", statement.getText(0))
            assertFalse(statement.step())
        }
    }

    @Test
    fun shouldBindAllSupportedTypesAndReadThemBack() {
        createUserTable()
        val blob = byteArrayOf(0x00, 0x01, -0x80, 0x42)
        connection.prepare(
            "INSERT INTO user (name, age, email, score, avatar, flag) VALUES (?, ?, ?, ?, ?, ?)"
        ).use { stmt ->
            stmt.bindText(1, "John Doe")
            stmt.bindInt(2, 42)
            stmt.bindText(3, "john@doe.com")
            stmt.bindDouble(4, 3.14159)
            stmt.bindBlob(5, blob)
            stmt.bindBoolean(6, true)
            assertFalse(stmt.step())
        }
        connection.prepare("SELECT id, name, age, email, score, avatar, flag FROM user").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(7, stmt.getColumnCount())
            assertTrue(stmt.getLong(0) > 0L)
            assertEquals("John Doe", stmt.getText(1))
            assertEquals(42, stmt.getInt(2))
            assertEquals("john@doe.com", stmt.getText(3))
            assertEquals(3.14159, stmt.getDouble(4), 1e-9)
            assertArrayEquals(blob, stmt.getBlob(5))
            assertEquals(true, stmt.getBoolean(6))

            assertEquals("name", stmt.getColumnName(1))
            assertEquals("age", stmt.getColumnName(2))
            assertEquals("email", stmt.getColumnName(3))
            assertEquals("score", stmt.getColumnName(4))
            assertEquals("avatar", stmt.getColumnName(5))
            assertEquals("flag", stmt.getColumnName(6))
        }
    }

    @Test
    fun shouldRebindStatementAfterResetForStatementReuse() {
        createUserTable()
        connection.prepare("INSERT INTO user (name) VALUES (?)").use { stmt ->
            listOf("one", "two", "three").forEach { name ->
                stmt.reset()
                stmt.clearBindings()
                stmt.bindText(1, name)
                assertFalse(stmt.step())
            }
        }
        connection.prepare("SELECT count(*) FROM user").use { stmt ->
            stmt.step()
            assertEquals(3L, stmt.getLong(0))
        }
    }

    @Test
    fun shouldThrowWhenUsingClosedStatement() {
        createUserTable()
        val stmt = connection.prepare("SELECT count(*) FROM user")
        stmt.close()
        assertThrows(android.database.SQLException::class.java) {
            stmt.step()
        }
    }

    private fun exec(sql: String) {
        connection.prepare(sql).use { it.step() }
    }

    private fun queryLong(sql: String): Long =
        connection.prepare(sql).use { statement ->
            assertTrue("expected a row from: $sql", statement.step())
            statement.getLong(0)
        }

    private fun createUserTable(conn: SQLiteConnection = connection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                age INTEGER,
                email TEXT UNIQUE,
                score REAL,
                avatar BLOB,
                flag INTEGER
            )
            """.trimIndent()
        )
    }
}
