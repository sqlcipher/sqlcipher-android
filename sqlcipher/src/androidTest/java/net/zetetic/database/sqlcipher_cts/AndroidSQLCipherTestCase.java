package net.zetetic.database.sqlcipher_cts;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Locale;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public abstract class AndroidSQLCipherTestCase {

  protected SQLiteDatabase database;
  protected String DATABASE_NAME = "database_test.db";
  protected String TAG = getClass().getSimpleName();
  protected File databasePath = null;
  protected Context context = null;

  @Before
  public void setUp() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    System.loadLibrary("sqlcipher");
    databasePath = context.getDatabasePath(DATABASE_NAME);
    databasePath.mkdirs();
    if (databasePath.exists()) {
      databasePath.delete();
    }
  }

  @After
  public void tearDown() {
    if(database != null){
      database.close();
    }
    if(context != null){
      context.deleteDatabase(DATABASE_NAME);
    }
  }

  protected void log(String message, Object...args){
    Log.i(TAG, String.format(Locale.getDefault(), message, args));
  }
}
