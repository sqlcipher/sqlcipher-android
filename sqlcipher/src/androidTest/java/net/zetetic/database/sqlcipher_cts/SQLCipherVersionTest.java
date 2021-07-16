package net.zetetic.database.sqlcipher_cts;

import android.database.Cursor;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.StringContains.containsString;

public class SQLCipherVersionTest extends AndroidSQLCipherTestCase {

  @Test
  public void testCipherVersionReported(){
    String cipherVersion = "";
    Cursor cursor = database.rawQuery("PRAGMA cipher_version;", new String[]{});
    if(cursor != null && cursor.moveToFirst()){
      cipherVersion = cursor.getString(0);
      cursor.close();
    }
    assertThat(cipherVersion, containsString("4.4.3"));
  }
}
