package net.zetetic.database.sqlcipher_cts;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;

import org.junit.Test;

import java.io.File;

public class MigrateDatabaseFrom1xFormatToCurrentFormatTest extends AndroidSQLCipherTestCase {

  @Test
  public void shouldPerformMigrationFrom1xFileFormatToCurrent() {
    File oldDatabaseFileFormatPath = null;
    try {
      database.close();
      String password = "test", a = "", b = "";
      oldDatabaseFileFormatPath = extractAssetToDatabaseDirectory("sqlcipher-1.x-test.db");
      SQLiteDatabaseHook hook = new SQLiteDatabaseHook() {
        public void preKey(SQLiteConnection database) { }
        public void postKey(SQLiteConnection database) {
          long result = database.executeForLong("PRAGMA cipher_migrate;", null, null);
          assertThat("PRAGMA cipher_migrate should return 0 for success", result, is(0L));
        }
      };
      database = SQLiteDatabase.openDatabase(oldDatabaseFileFormatPath.getPath(), password, null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY, null, hook);
      database.close();
      database = SQLiteDatabase.openDatabase(oldDatabaseFileFormatPath.getPath(), password, null, SQLiteDatabase.OPEN_READWRITE, null, null);
      Cursor result = database.rawQuery("select * from t1", new String[]{});
      if (result != null && result.moveToFirst()) {
        a = result.getString(0);
        b = result.getString(1);
        result.close();
      }
      database.close();
      assertThat(a, is("one for the money"));
      assertThat(b, is("two for the show"));
    } finally {
      delete(oldDatabaseFileFormatPath);
    }
  }
}
