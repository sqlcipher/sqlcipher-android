package net.zetetic.database.sqlcipher_cts.support;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class SupportAPIRoomTest {

  private AppDatabase db;

  @Before
  public void before(){
    Context context = InstrumentationRegistry.getInstrumentation().getContext();
//    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
//    Context context = ApplicationProvider.getApplicationContext();
    System.loadLibrary("sqlcipher");
    SupportOpenHelperFactory factory = new SupportOpenHelperFactory("user".getBytes(StandardCharsets.UTF_8));
    db = Room.databaseBuilder(context, AppDatabase.class, "users.db")
        .openHelperFactory(factory).build();
  }

  @Test
  public void shouldInsertData(){
    UserDao userDao = db.userDao();
    User user = new User("John", "Doe");
    userDao.insertAll(user);
    assertThat(user.uid, is(not(0)));
  }

  @After
  public void after(){
    if(db != null){
      db.close();
    }
  }

}
