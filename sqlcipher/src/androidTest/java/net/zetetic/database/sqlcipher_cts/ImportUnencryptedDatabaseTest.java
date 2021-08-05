package net.zetetic.database.sqlcipher_cts;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.junit.Test;

import java.io.File;

public class ImportUnencryptedDatabaseTest extends AndroidSQLCipherTestCase {

  @Test
  public void shouldImportUnencryptedPlaintextDatabase(){
    String a = null, b = null;
    File unencryptedDatabasePath = null;
    File encryptedDatabasePath = context.getDatabasePath("encrypted.db");
    try {
      database.close();
      unencryptedDatabasePath = extractAssetToDatabaseDirectory("unencrypted.db");
      database = SQLiteDatabase.openDatabase(unencryptedDatabasePath.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
      database.execSQL("ATTACH DATABASE ? as encrypted KEY ?;",
          new Object[]{encryptedDatabasePath.getAbsolutePath(), "foo"});
      database.execSQL("SELECT sqlcipher_export('encrypted');");
      database.execSQL("DETACH DATABASE encrypted;");
      database.close();

      database = SQLiteDatabase.openDatabase(encryptedDatabasePath.getAbsolutePath(), "foo",
          null, SQLiteDatabase.OPEN_READWRITE, null);
      Cursor cursor = database.rawQuery("select * from t1", new String[]{});
      if(cursor != null && cursor.moveToFirst()) {
        a = cursor.getString(0);
        b = cursor.getString(1);
        cursor.close();
      }
      database.close();
      assertThat(a, is("one for the money"));
      assertThat(b, is("two for the show"));

    } catch (Exception ex){

    } finally {
      delete(unencryptedDatabasePath);
      delete(encryptedDatabasePath);
    }
  }

}
