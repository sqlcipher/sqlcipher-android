package net.zetetic.database.sqlcipher_cts;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import android.content.Context;
import android.database.Cursor;

import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Delete;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SupportAPIRoomTest {

  private AppDatabase db;
  private UserDao userDao;

  @Before
  public void before(){
    Context context = ApplicationProvider.getApplicationContext();
    System.loadLibrary("sqlcipher");
    SupportOpenHelperFactory factory = new SupportOpenHelperFactory("user".getBytes(StandardCharsets.UTF_8));
    db = Room.databaseBuilder(context, AppDatabase.class, "users.db")
        .openHelperFactory(factory).build();
    db.clearAllTables();
    userDao = db.userDao();
  }

  @Test
  public void shouldInsertDataViaDao(){
    User user = new User("John", "Doe");
    user.uid = userDao.insert(user);
    assertThat(user.uid, is(not(0)));
  }

  @Test
  public void shouldDeleteDataViaDao(){
    User user = new User("foo", "bar");
    user.uid = 1;
    SupportSQLiteOpenHelper helper = db.getOpenHelper();
    SupportSQLiteDatabase database = helper.getWritableDatabase();
    database.execSQL("insert into user values(?,?,?);",
        new Object[]{user.uid, user.firstName, user.lastName});
    Cursor cursor = database.query("select count(*) from user;");
    cursor.moveToFirst();
    boolean userInserted = cursor.getInt(0) > 0;
    assertThat(userInserted, is(true));
    userDao.delete(user);
    cursor = database.query("select count(*) from user;");
    cursor.moveToFirst();
    int existingUsers = cursor.getInt(0);
    cursor.close();
    assertThat(existingUsers, is(0));
  }

  @Test
  public void shouldQueryDataByParametersViaDao(){
    User user = new User("foo", "bar");
    user.uid = 1;
    SupportSQLiteOpenHelper helper = db.getOpenHelper();
    SupportSQLiteDatabase database = helper.getWritableDatabase();
    database.execSQL("insert into user values(?,?,?);",
        new Object[]{user.uid, user.firstName, user.lastName});
    User foundUser = userDao.findByName(user.firstName, user.lastName);
    assertThat(foundUser.uid, is(user.uid));
    assertThat(foundUser.firstName, is(user.firstName));
    assertThat(foundUser.lastName, is(user.lastName));
  }

  @After
  public void after(){
    if(db != null){
      db.close();
    }
  }

  @Database(entities = {User.class}, version = 1)
  public abstract static class AppDatabase extends RoomDatabase {
    public abstract UserDao userDao();
  }

  @Entity
  public static class User {
    @PrimaryKey(autoGenerate = true)
    public long uid;
    @ColumnInfo(name = "first_name")
    public String firstName;
    @ColumnInfo(name = "last_name")
    public String lastName;

    public User(String firstName, String lastName) {
      this.firstName = firstName;
      this.lastName = lastName;
    }
  }

  @Dao
  public interface UserDao {
    @Query("SELECT * FROM user")
    List<User> getAll();

    @Query("SELECT * FROM user WHERE uid IN (:userIds)")
    List<User> loadAllByIds(int[] userIds);

    @Query("SELECT * FROM user WHERE first_name LIKE :first AND " +
        "last_name LIKE :last LIMIT 1")
    User findByName(String first, String last);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(User user);

    @Delete
    void delete(User user);

    @Query("DELETE FROM user;")
    void deleteAll();
  }
}
