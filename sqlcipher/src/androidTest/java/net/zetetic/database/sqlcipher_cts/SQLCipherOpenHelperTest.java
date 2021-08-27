package net.zetetic.database.sqlcipher_cts;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import android.content.Context;
import android.util.Log;

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;
import net.zetetic.database.sqlcipher.SQLiteStatement;

import org.junit.Test;

public class SQLCipherOpenHelperTest extends AndroidSQLCipherTestCase {

  @Test
  public void shouldAccessReadOnlyDatabaseFromOpenHelper(){
    database.close();
    SqlCipherOpenHelper helper = new SqlCipherOpenHelper(context);
    SQLiteDatabase db = helper.getReadableDatabase();
    SQLiteStatement statement = db.compileStatement("PRAGMA cipher_provider_version;");
    String version = statement.simpleQueryForString();
    Log.i(TAG, String.format("SQLCipher provider version:%s", version));
    assertThat(db, is(notNullValue()));
  }

  private class SqlCipherOpenHelper extends SQLiteOpenHelper {

    private final String TAG = getClass().getSimpleName();

    public SqlCipherOpenHelper(Context context) {
      super(context, "test.db", "test", null, 1, 1, sqLiteDatabase -> Log.e(SQLCipherOpenHelperTest.this.TAG, "onCorruption()"), new SQLiteDatabaseHook() {
        @Override
        public void preKey(SQLiteConnection sqLiteConnection) {
          Log.d(SQLCipherOpenHelperTest.this.TAG, "preKey()");
        }

        @Override
        public void postKey(SQLiteConnection sqLiteConnection) {
          Log.d(SQLCipherOpenHelperTest.this.TAG, "postKey()");
        }
      });
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL("CREATE TABLE test (_id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
  }

}
