package net.zetetic.database.sqlcipher_cts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import android.database.Cursor;

import org.junit.Test;

public class SQLCipherVersionTest extends AndroidSQLCipherTestCase {

  @Test
  public void shouldExtractLibraryCipherVersion() {
    String cipherVersion = "";
    Cursor cursor = database.rawQuery("PRAGMA cipher_version;");
    if(cursor != null && cursor.moveToFirst()){
      cipherVersion = cursor.getString(0);
      cursor.close();
    }
    assertThat(cipherVersion, containsString("4.5.6"));
  }
}
