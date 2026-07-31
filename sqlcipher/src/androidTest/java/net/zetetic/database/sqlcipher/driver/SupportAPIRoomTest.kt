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
import androidx.sqlite.SQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

@RunWith(AndroidJUnit4::class)
class SupportAPIRoomTest {
    private lateinit var driver: SQLiteDriver
    private lateinit var db: AppDatabase
    private lateinit var userDao: UserDao
    private lateinit var databaseFile: File

    @Before
    fun setup(){
        val password = "user".toByteArray(StandardCharsets.UTF_8)
        setup(password, true)
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
        db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseFile.absolutePath)
        .setDriver(driver)
        .build()
        userDao = db.userDao()
    }

    @Test
    fun shouldInsertDataViaDao() {
        val user = User("John", "Doe")
        user.uid = userDao.insert(user)
        assertThat(user.uid, not(0L))
    }

    @Test
    @Throws(InterruptedException::class)
    fun shouldDeleteDataViaDao() = runTest {
        val user = User("foo", "bar").apply { uid = 1 }
        userDao.insert(user)
        assertThat(userDao.findById(user.uid), notNullValue())
        userDao.delete(user)
        assertThat(userDao.all, `is`(empty()))
    }

    @Test
    fun shouldQueryDataByParametersViaDao(){
      val user = User("foo", "bar").apply { uid = 1 }
      userDao.insert(user)
      val foundUser = userDao.findByName(user.firstName, user.lastName)
      assertThat(foundUser, notNullValue())
      assertThat(foundUser!!.uid, `is`(user.uid))
      assertThat(foundUser.firstName, `is`(user.firstName))
      assertThat(foundUser.lastName, `is`(user.lastName))
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

    @After
    fun after() {
        db.close()
        databaseFile.delete()
    }

    @Database(entities = [User::class], version = 1, exportSchema = false)
    abstract class AppDatabase : RoomDatabase() {
        abstract fun userDao(): UserDao
    }

    @Entity
    class User(
        @field:ColumnInfo(name = "first_name") var firstName: String,
        @field:ColumnInfo(name = "last_name") var lastName: String
    ) {
        @PrimaryKey(autoGenerate = true)
        var uid: Long = 0
    }

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

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun insert(user: User): Long

        @Delete
        fun delete(user: User)

        @Query("DELETE FROM user;")
        fun deleteAll()
    }
}
