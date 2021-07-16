package net.zetetic.database.sqlcipher_cts;

import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;

import org.junit.Test;

import java.io.File;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class SQLCipherDatabaseTest extends AndroidSQLCipherTestCase {

  @Test
  public void testConnectionWithPassword(){
    int a = 0, b = 0;
    database.close();
    database = null;
    File databasePath = context.getDatabasePath("foo.db");
    if(databasePath.exists()){
      databasePath.delete();
    }
    database = SQLiteDatabase.openOrCreateDatabase(databasePath, "foo", null, null);
    database.execSQL("create table t1(a,b);");
    database.execSQL("insert into t1(a,b) values(?,?)", new Object[]{1, 2});
    Cursor cursor = database.rawQuery("select * from t1;", new String[]{});
    if(cursor != null && cursor.moveToFirst()){
      a = cursor.getInt(0);
      b = cursor.getInt(1);
    }
    assertThat(a, is(1));
    assertThat(b, is(2));
  }
}
