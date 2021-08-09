package net.zetetic.database.sqlcipher_cts;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import android.content.Context;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.junit.Test;

public class SQLCipherOpenHelperTest extends AndroidSQLCipherTestCase {

  @Test
  public void shouldAccessReadOnlyDatabaseFromOpenHelper(){
    database.close();
    DatabaseHelper helper = new DatabaseHelper(context);
    SQLiteDatabase db = helper.getReadableDatabase();
    assertThat(db, is(notNullValue()));
  }

  private class DatabaseHelper extends SQLiteOpenHelper {

    public DatabaseHelper(Context context){
      super(context, "foo.db", "foo", null, 1, 1, null, null);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
  }

}
