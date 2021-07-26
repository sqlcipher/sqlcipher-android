package net.zetetic.database.sqlcipher_cts;

import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseConfiguration;
import net.zetetic.database.sqlcipher.SQLiteException;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;

public class SQLCipherDatabaseTest extends AndroidSQLCipherTestCase {

  @Test
  public void testConnectionWithPassword(){
    int a = 0, b = 0;
    database = SQLiteDatabase.openOrCreateDatabase(databasePath, "foo", null, null);
    database.execSQL("create table t1(a,b);");
    database.execSQL("insert into t1(a,b) values(?,?)", new Object[]{1, 2});
    Cursor cursor = database.rawQuery("select * from t1;", new String[]{});
    if(cursor != null && cursor.moveToFirst()){
      a = cursor.getInt(0);
      b = cursor.getInt(1);
      cursor.close();
    }
    assertThat(a, is(1));
    assertThat(b, is(2));
  }

  @Test
  public void insertDataQueryByObjectParams(){
    float a = 1.25f, a1 = 0.0f;
    double b = 2.00123d, b1 = 0.0d;
    database = SQLiteDatabase.openOrCreateDatabase(databasePath, "foo", null, null);
    database.execSQL("create table t1(a,b);");
    database.execSQL("insert into t1(a,b) values(?,?)", new Object[]{a, b});
    Cursor cursor = database.rawQuery("select * from t1 where a = ? and b = ?;", a, b);
    if(cursor != null && cursor.moveToFirst()){
      a1 = cursor.getFloat(0);
      b1 = cursor.getDouble(1);
      cursor.close();
    }
    assertThat(a1, is(a));
    assertThat(b1, is(b));
  }

  @Test
  public void shouldChangeDatabasePasswordSuccessfully(){
    int a = 3, b = 4;
    int a1 = 0, b1 = 0;
    String originalPassword = "foo", newPassword = "bar";
    database = SQLiteDatabase.openOrCreateDatabase(databasePath, originalPassword, null, null);
    database.execSQL("create table t1(a,b);");
    database.execSQL("insert into t1(a,b) values(?,?)", new Object[]{a, b});
    database.changePassword(newPassword);
    database.close();
    database = null;
    try {
      database = SQLiteDatabase.openOrCreateDatabase(databasePath, originalPassword, null, null);
      assertThat(database, is(nullValue()));
    } catch (SQLiteException ex){
      database = SQLiteDatabase.openOrCreateDatabase(databasePath, newPassword, null, null);
      Cursor cursor = database.rawQuery("select * from t1;");
      if(cursor != null && cursor.moveToFirst()){
        a1 = cursor.getInt(0);
        b1 = cursor.getInt(1);
        cursor.close();
      }
      assertThat(a1, is(a));
      assertThat(b1, is(b));
    }
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowExceptionWhenChangingPasswordOnClosedDatabase(){
    database = SQLiteDatabase.openOrCreateDatabase(databasePath, "foo", null, null);
    database.close();
    database.changePassword("bar");
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowExceptionOnChangePasswordWithReadOnlyDatabase(){
    database = SQLiteDatabase.openOrCreateDatabase(databasePath.getAbsolutePath(), "foo", null, null);
    database.execSQL("create table t1(a,b);");
    database.close();
    database = null;
    database = SQLiteDatabase.openDatabase(databasePath.getAbsolutePath(), "foo", null, SQLiteDatabase.OPEN_READONLY, null, null);
    database.changePassword("bar");
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowExceptionOnChangePasswordWithInMemoryDatabase(){
    database = SQLiteDatabase.openOrCreateDatabase(SQLiteDatabaseConfiguration.MEMORY_DB_PATH, "foo", null, null, null);
    database.changePassword("bar");
  }
}
