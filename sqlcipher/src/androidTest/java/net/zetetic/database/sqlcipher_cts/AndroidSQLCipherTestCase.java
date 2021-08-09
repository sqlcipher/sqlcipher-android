package net.zetetic.database.sqlcipher_cts;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public abstract class AndroidSQLCipherTestCase {

  protected SQLiteDatabase database;
  protected static String DATABASE_NAME = "database_test.db";
  protected String TAG = getClass().getSimpleName();
  protected File databaseFilePath = null;
  protected Context context = null;

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Before
  public void setUp() {
    context = InstrumentationRegistry.getInstrumentation().getContext();
    System.loadLibrary("sqlcipher");
    databaseFilePath = context.getDatabasePath(DATABASE_NAME);
    databaseFilePath.mkdirs();
    if (databaseFilePath.exists()) {
      databaseFilePath.delete();
    }
    database = SQLiteDatabase.openOrCreateDatabase(databaseFilePath, "foo", null, null, null);
  }

  @After
  public void tearDown() {
    if (database != null) {
      database.close();
    }
    if (context != null) {
      context.deleteDatabase(DATABASE_NAME);
    }
  }

  public File extractAssetToDatabaseDirectory(String fileName) {
    File destinationPath = null;
    try {
      int length;
      InputStream sourceDatabase = context.getAssets().open(fileName);
      destinationPath = context.getDatabasePath(fileName);
      OutputStream destination = new FileOutputStream(destinationPath);
      byte[] buffer = new byte[4096];
      while ((length = sourceDatabase.read(buffer)) > 0) {
        destination.write(buffer, 0, length);
      }
      sourceDatabase.close();
      destination.flush();
      destination.close();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    return destinationPath;
  }

  public void delete(File filePath) {
    if (filePath == null) return;
    if (filePath.exists()) {
      boolean result = filePath.delete();
      log("Deleted file:%s result:%s", filePath.getAbsolutePath(), result);
    }
  }

  public void closeAndDelete(SQLiteDatabase database) {
    if (database == null) return;
    File path = new File(database.getPath());
    database.close();
    if (path.exists()) {
      boolean result = path.delete();
      log("Deleted database:%s result:%s", path.getAbsolutePath(), result);
    }
  }

  public byte[] generateRandomBytes(int length) {
    SecureRandom random = new SecureRandom();
    byte[] data = new byte[length];
    random.nextBytes(data);
    return data;
  }

  protected void log(String message, Object... args) {
    Log.i(TAG, String.format(Locale.getDefault(), message, args));
  }

  protected void loge(Exception ex, String message, Object... args) {
    Log.e(TAG, String.format(Locale.getDefault(), message, args), ex);
  }
}
