package net.zetetic.database.sqlcipher.driver

import android.content.Context
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Upsert
import androidx.room3.support.getSupportWrapper
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

@RunWith(AndroidJUnit4::class)
class SupportAPIRoomTest {
    private lateinit var driver: SQLiteDriver
    private lateinit var db: AppDatabase
    private lateinit var userDao: UserDao
    private lateinit var databaseFile: File
    private lateinit var connection: SQLiteConnection
    private val defaultPassword = "user".toByteArray(StandardCharsets.UTF_8)

    @Before
    fun setup(){
        setup(defaultPassword, true)
    }

    fun setup(password: ByteArray, deleteDatabase: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        System.loadLibrary("sqlcipher")
        databaseFile = context.getDatabasePath("users.db")
        if (deleteDatabase and databaseFile.exists()) {
            databaseFile.delete()
        }
        driver = SQLCipherDriver(
            password,
            null,
            null
        )
        connection = driver.open(databaseFile.absolutePath);
        db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseFile.absolutePath)
        .setDriver(driver)
        .build()
        userDao = db.userDao()
    }

    @After
    fun after() {
        db.close()
        databaseFile.delete()
    }

    @Test
    fun shouldInsertDataViaDao() {
        val user = User("John", "Doe")
        val uid = userDao.insert(user)
        assertThat(uid, not(0L))
    }

    @Test
    @Throws(InterruptedException::class)
    fun shouldDeleteDataViaDao() = runTest {
        val user = User(uid = 1, firstName = "foo", lastName = "bar")
        userDao.insert(user)
        assertThat(userDao.findById(user.uid), notNullValue())
        userDao.delete(user)
        assertThat(userDao.all, `is`(empty()))
    }

    @Test
    fun shouldQueryDataByParametersViaDao(){
      val user = User(uid = 1, firstName = "foo", lastName = "bar")
      userDao.insert(user)
      val foundUser = userDao.findByName(user.firstName, user.lastName)
      assertThat(foundUser, notNullValue())
      assertThat(foundUser!!.uid, `is`(user.uid))
      assertThat(foundUser.firstName, `is`(user.firstName))
      assertThat(foundUser.lastName, `is`(user.lastName))
    }

    @Test
    fun shouldReplaceExistingRowWithReplaceConflictStrategy() = runTest {
        val original = User(firstName = "John", lastName = "Doe")
        val id = userDao.insert(original)

        val updated = original.copy(uid = id, firstName = "Jane")
        userDao.insertOrReplace(updated)

        val all = userDao.all
        assertEquals(1, all.size)
        with(all.single()) {
            assertEquals(id, uid)
            assertEquals("Jane", firstName)
            assertEquals("Doe", lastName)
        }
    }

    @Test
    fun shouldAllowUpsertBehavior() {
        val user = User(firstName = "John", lastName = "Doe")
        val uid = userDao.upsert(user)
        userDao.upsert(user.copy(uid = uid, firstName = "Jane"))
        with(userDao.all.single()) {
            assertEquals(uid, uid)
            assertEquals("Jane", firstName)
            assertEquals("Doe", lastName)
        }
    }

    @Test
    fun shouldRollbackWriteTransactionOnCancellation() = runTest {
        try {
            db.withWriteTransaction {
                userDao.insert(User(firstName = "foo", lastName = "bar"))
                throw CancellationException("cancelled mid-transaction")
            }
        } catch (_: CancellationException) {}
        assertEquals(0, userDao.all.count())
    }

    @Test
    fun shouldSeeOwnWritesInsideWriteTransaction() = runTest {
        db.withWriteTransaction {
            userDao.insert(User(firstName = "foo", lastName = "bar"))
            assertEquals(1, userDao.all.count())
        }
    }

    @Test
    fun shouldSupportChangingPasswordWithRoom() = runTest {
      val newPassword = "foobar".toByteArray()
      userDao.insert( User("foo", "bar"))
      db.close()
      val connection = driver.open(databaseFile.absolutePath) as SQLCipherConnection
      connection.changePassword(newPassword)
      connection.close()
      setup(newPassword, false)
      assertThat(userDao.all.count(), `is`(1))
    }

    @Test
    fun shouldVerifyWalModeFromExistingDatabase() = runTest {
        connection.prepare("PRAGMA journal_mode = wal;").use { stmt ->
            stmt.step();
            val mode = stmt.getText(0)
            assertThat(mode, `is`("wal"))
        }
        connection.close()
        setup(defaultPassword, false)
        assertThat(db.getSupportWrapper().isWriteAheadLoggingEnabled, `is`(true))
    }

    @Test
    fun shouldReadNullColumnThroughSupportWrapperCursor() {
        db.getSupportWrapper().query("SELECT NULL AS empty_value, 'x' AS present_value").use { cursor ->
            assertThat(cursor.moveToFirst(), `is`(true))
            assertThat(cursor.isNull(0), `is`(true))
            assertThat(cursor.getString(1), `is`("x"))
        }
    }

    @Database(entities = [User::class], version = 1, exportSchema = false)
    abstract class AppDatabase : RoomDatabase() {
        abstract fun userDao(): UserDao
    }

    @Entity
    data class User(
        @ColumnInfo(name = "first_name") val firstName: String,
        @ColumnInfo(name = "last_name") val lastName: String,
        @PrimaryKey(autoGenerate = true) val uid: Long = 0
    )

    @Dao
    interface UserDao {
        @get:Query("SELECT * FROM user")
        val all: MutableList<User>

        @Query("SELECT * FROM user WHERE uid IN (:userIds)")
        fun loadAllByIds(userIds: IntArray?): MutableList<User>

        @Query(
            "SELECT * FROM user WHERE first_name LIKE :first AND " +
                    "last_name LIKE :last LIMIT 1"
        )
        fun findByName(first: String?, last: String?): User?

        @Query("SELECT * FROM user WHERE uid = :userId")
        fun findById(userId: Long): User?

        @Insert
        fun insert(user: User): Long

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun insertOrReplace(user: User): Long

        @Upsert
        fun upsert(user: User): Long

        @Delete
        fun delete(user: User)

        @Query("DELETE FROM user;")
        fun deleteAll()
    }
}
