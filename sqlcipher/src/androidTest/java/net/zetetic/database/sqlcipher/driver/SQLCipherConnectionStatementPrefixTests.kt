package net.zetetic.database.sqlcipher.driver

import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SQLCipherConnectionStatementPrefixTests {

    @Test
    fun shouldExtractPrefixFromPlainStatements() {
        assertEquals("SEL", SQLCipherConnection.statementPrefix("SELECT * FROM t"))
        assertEquals("BEG", SQLCipherConnection.statementPrefix("BEGIN"))
        assertEquals("PRA", SQLCipherConnection.statementPrefix("PRAGMA user_version"))
    }

    @Test
    fun shouldSkipLeadingWhitespace() {
        assertEquals("BEG", SQLCipherConnection.statementPrefix("   \n\t BEGIN IMMEDIATE"))
    }

    @Test
    fun shouldSkipLineComments() {
        assertEquals("BEG", SQLCipherConnection.statementPrefix("-- start txn\nBEGIN IMMEDIATE"))
    }

    @Test
    fun shouldSkipBlockComments() {
        assertEquals("COM", SQLCipherConnection.statementPrefix("/* finish\n the txn */ COMMIT"))
    }

    @Test
    fun shouldReturnNullForCommentOnlyInput() {
        assertNull(SQLCipherConnection.statementPrefix("-- nothing here"))
        assertNull(SQLCipherConnection.statementPrefix("/* nothing here */"))
    }

    @Test
    fun shouldTolerateShortInput() {
        assertNull(SQLCipherConnection.statementPrefix(""))
        assertNull(SQLCipherConnection.statementPrefix(" "))
    }

    @Test
    fun shouldInterceptJournalModeSets() {
        assertTrue(journalFor("PRAGMA journal_mode = WAL"))
        assertTrue(journalFor("pragma journal_mode=delete"))
        assertTrue(journalFor("PRAGMA main.journal_mode = TRUNCATE"))
    }

    @Test
    fun shouldNotInterceptJournalModeQueries() {
        assertFalse(journalFor("PRAGMA journal_mode"))
    }

    @Test
    fun shouldNotInterceptOtherPragmas() {
        assertFalse(journalFor("PRAGMA foreign_keys = ON"))
        assertFalse(journalFor("PRAGMA busy_timeout = 3000"))
        assertFalse(journalFor("PRAGMA synchronous = NORMAL"))
    }

    private fun journalFor(sql: String): Boolean {
        val trimmed = sql.trim()
        return SQLCipherConnection.isJournalModeSet(
            SQLCipherConnection.statementPrefix(trimmed),
            trimmed)
    }
}