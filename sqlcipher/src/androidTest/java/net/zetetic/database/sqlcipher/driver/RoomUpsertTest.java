package net.zetetic.database.sqlcipher.driver;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import androidx.room3.Dao;
import androidx.room3.Database;
import androidx.room3.Entity;
import androidx.room3.PrimaryKey;
import androidx.room3.Query;
import androidx.room3.Room;
import androidx.room3.RoomDatabase;
import androidx.room3.Upsert;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.zetetic.database.sqlcipher.driver.SQLCipherDriver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class RoomUpsertTest {
    private SQLCipherDriver driver;
	private UserDatabase database;
	private UserDao userDao;

	@Before
	public void before(){
		var context = ApplicationProvider.getApplicationContext();
		var databaseFile = context.getDatabasePath("upsert.db");
		if(databaseFile.exists()){
			databaseFile.delete();
		}
		System.loadLibrary("sqlcipher");
		var passphrase = "user".getBytes(StandardCharsets.UTF_8);
        driver = new SQLCipherDriver(
                passphrase,
            null,
            null);
        database = Room.databaseBuilder(
                        context,
                        UserDatabase.class,
                        "users.db")
                .setDriver(driver)
                .build();
		userDao = database.userDao();
	}

	@Test
	public void shouldAllowUpsertBehavior(){
		var user = new User();
		user.name = "Foo Bar";
		user.age = 41;
		user.id = userDao.upsert(user);
		user.age = 42;
		userDao.upsert(user);
		var searchUser = userDao.findById(user.id);
		assertThat(searchUser[0].age , is(42));
	}

	@Entity
	public static class User {
		@PrimaryKey(autoGenerate = true) long id;
		String name;
		int age;
	}

	@Dao
	public static abstract class UserDao {
		@Upsert
		abstract long upsert(User user);
		@Query("SELECT * FROM user WHERE id=:id")
		abstract User[] findById(long id);
	}

	@Database(entities = {User.class}, version = 1, exportSchema = false)
	public static abstract class UserDatabase extends RoomDatabase {
		abstract UserDao userDao();
	}

}
