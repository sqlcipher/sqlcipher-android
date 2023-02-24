package net.zetetic.database.sqlcipher_cts;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import android.content.Context;

import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.Upsert;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class RoomUpsertTest {
	private UserDatabase database;
	private UserDao userDao;

	@Before
	public void before(){
		Context context = ApplicationProvider.getApplicationContext();
		File databaseFile = context.getDatabasePath("upsert.db");
		if(databaseFile.exists()){
			databaseFile.delete();
		}
		System.loadLibrary("sqlcipher");
		final byte[] passphrase = "user".getBytes(StandardCharsets.UTF_8);
		SupportOpenHelperFactory factory = new SupportOpenHelperFactory(passphrase);
		database = Room.databaseBuilder(context, UserDatabase.class, databaseFile.getName())
			.openHelperFactory(factory).build();
		userDao = database.userDao();
	}

	@Test
	public void shouldAllowUpsertBehavior(){
		User user = new User();
		user.name = "Foo Bar";
		user.age = 41;
		user.id = userDao.upsert(user);
		user.age = 42;
		userDao.upsert(user);
		User[] searchUser = userDao.findById(user.id);
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

	@Database(entities = {User.class}, version = 1)
	public static abstract class UserDatabase extends RoomDatabase {
		abstract UserDao userDao();
	}

}
