package net.zetetic.database.sqlcipher_cts;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import android.database.Cursor;

import org.junit.Test;

public class JsonCastTest extends AndroidSQLCipherTestCase {

  @Test
  public void shouldExtractUsernameFromQuery(){
    String name = "Bob Smith", queryName = "";
    String query = String.format("select cast(json_extract('{\"user\":\"%s\"}','$.user') as TEXT);", name);
    Cursor cursor = database.rawQuery(query);
    if(cursor != null && cursor.moveToFirst()){
      queryName = cursor.getString(0);
      cursor.close();
    }
    assertThat(queryName, is(name));
  }

}
