package net.zetetic.database.sqlcipher.driver;

import android.content.Context;

import androidx.room3.Dao;
import androidx.room3.Database;
import androidx.room3.Entity;
import androidx.room3.Insert;
import androidx.room3.PrimaryKey;
import androidx.room3.Query;
import androidx.room3.Room;
import androidx.room3.RoomDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.zetetic.database.sqlcipher.driver.SQLCipherDriver;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Room3SQLCipherDriverTest {

    private Context context;
    private SQLCipherDriver driver;

    @Before
    public void setup(){
        context = ApplicationProvider.getApplicationContext();
        System.loadLibrary("sqlcipher");
        driver = new SQLCipherDriver(
            "password".getBytes(),
            null,
            null);
    }

    @Test
    public void shouldTestSQLCipherDriver(){
        var database = Room.databaseBuilder(
            context,
            UserDatabase.class,
        "users.db")
        .setDriver(driver)
        .build();
        var dao = database.userDao();
        var name = "Nick Parker";
        dao.insert(new User(1, name));
        var result = dao.get(1);

        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.id);
        Assert.assertEquals(name, result.name);
        database.close();
    }

    @Test
    public void shouldRetrieveCipherVersion(){
        var connection = driver.open(":memory:");
        var stmt = connection.prepare("PRAGMA cipher_version;");
        stmt.step();
        var version = stmt.getText(0);
        Assert.assertEquals("4.17.0 community", version);
        connection.close();
    }

    @Entity
    public static class User {
        @PrimaryKey
        public long id;
        public String name;

        public User(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Dao
    public interface UserDao {
        @Insert
        void insert(User user);

        @Query("SELECT * FROM User WHERE id = :id")
        User get(long id);
    }

    @Database(
    entities = {User.class},
    version = 1
    )
    public abstract static class UserDatabase extends RoomDatabase {
        public abstract UserDao userDao();
    }
}
