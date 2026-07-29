package net.zetetic.database.sqlcipher;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.MatcherAssert.assertThat;

import android.database.Cursor;

import org.junit.Test;

import java.io.File;

public class CipherCompatibilityTest extends AndroidSQLCipherTestCase {

  @Test
  public void shouldOpenSQLCipher3Database() {
    int count = 0;
    database.close();
    File file = null;
    try {
      file = extractAssetToDatabaseDirectory("sqlcipher-3.0-testkey.db");
      database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), "testkey", null, SQLiteDatabase.OPEN_READWRITE, hook);
      Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM sqlite_master;", null);
      if(cursor != null && cursor.moveToFirst()){
        count = cursor.getInt(0);
        cursor.close();
      }
      assertThat(count, greaterThan(0));
    } finally {
      delete(file);
    }
  }

  SQLiteDatabaseHook hook = new SQLiteDatabaseHook() {
    public void preKey(SQLiteConnection connection) {}
    public void postKey(SQLiteConnection connection) {
      connection.execute("PRAGMA cipher_compatibility = 3;", null, null);
    }
  };
}
