package net.zetetic.database.sqlcipher_cts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;

import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseConfiguration;
import net.zetetic.database.sqlcipher.SQLiteDatabaseCorruptException;
import net.zetetic.database.sqlcipher.SQLiteException;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class SQLCipherDatabaseTest extends AndroidSQLCipherTestCase {

  @Test
  public void testCreateDatabaseConnectionWithStringPassword() {
    try {
      int a = 0, b = 0;
      closeAndDelete(database);
      database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("foo.db"), "foo", null, null);
      database.execSQL("create table t1(a,b);");
      database.execSQL("insert into t1(a,b) values(?,?)", new Object[]{1, 2});
      database.close();
      database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("foo.db"), "foo", null, null);
      Cursor cursor = database.rawQuery("select * from t1;", new String[]{});
      if (cursor != null && cursor.moveToFirst()) {
        a = cursor.getInt(0);
        b = cursor.getInt(1);
        cursor.close();
      }
      assertThat(a, is(1));
      assertThat(b, is(2));
    } finally {
      delete(context.getDatabasePath("foo.db"));
    }
  }

  @Test
  public void testCreateDatabaseConnectionWithStringByteArray() {
    try {
      int a = 0, b = 0;
      closeAndDelete(database);
      byte[] generatedPassword = generateRandomBytes(64);
      database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("foo.db"), generatedPassword, null, null);
      database.execSQL("create table t1(a,b);");
      database.execSQL("insert into t1(a,b) values(?,?)", new Object[]{1, 2});
      database.close();
      database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("foo.db"), generatedPassword, null, null);
      Cursor cursor = database.rawQuery("select * from t1;", new String[]{});
      if (cursor != null && cursor.moveToFirst()) {
        a = cursor.getInt(0);
        b = cursor.getInt(1);
        cursor.close();
      }
      assertThat(a, is(1));
      assertThat(b, is(2));
    } finally {
      delete(context.getDatabasePath("foo.db"));
    }
  }

  @Test(expected = SQLiteDatabaseCorruptException.class)
  public void testOpenDatabaseConnectionWithInvalidStringPassword() {
    try {
      int a = 0, b = 0;
      closeAndDelete(database);
      database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("foo.db"), "foo", null, null);
      database.execSQL("create table t1(a,b);");
      database.execSQL("insert into t1(a,b) values(?,?)", new Object[]{1, 2});
      database.close();
      database = SQLiteDatabase.openDatabase(context.getDatabasePath("foo.db").getPath(), "bar", null, SQLiteDatabase.OPEN_READWRITE, null);
    } finally {
      delete(context.getDatabasePath("foo.db"));
    }
  }

  @Test(expected = SQLiteDatabaseCorruptException.class)
  public void testOpenDatabaseConnectionWithInvalidByteArrayPassword() {
    try {
      int a = 0, b = 0;
      closeAndDelete(database);
      byte[] initialPassword = generateRandomBytes(64);
      byte[] otherPassword = generateRandomBytes(64);
      database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("foo.db"), initialPassword, null, null);
      database.execSQL("create table t1(a,b);");
      database.execSQL("insert into t1(a,b) values(?,?)", new Object[]{1, 2});
      database.close();
      database = SQLiteDatabase.openDatabase(context.getDatabasePath("foo.db").getPath(), otherPassword, null, SQLiteDatabase.OPEN_READWRITE, null);
    } finally {
      delete(context.getDatabasePath("foo.db"));
    }
  }

  @Test
  public void openExistingSQLCipherDatabaseWithStringPassword(){
    File databasePath = null;
    int a = 0, b = 0;
    try {
      closeAndDelete(database);
      databasePath = extractAssetToDatabaseDirectory("sqlcipher-4.x-testkey.db");
      database = SQLiteDatabase.openDatabase(databasePath.getPath(), "testkey", null, SQLiteDatabase.OPEN_READWRITE, null);
      Cursor cursor = database.rawQuery("SELECT * FROM t1;");
      if(cursor != null && cursor.moveToFirst()){
        a = cursor.getInt(0);
        b = cursor.getInt(1);
        cursor.close();
      }
      assertThat(a, is(1));
      assertThat(b, is(2));
    } finally {
      delete(databasePath);
    }
  }

  @Test
  public void openExistingSQLCipherDatabaseWithByteArrayPassword(){
    File databasePath = null;
    int a = 0, b = 0;
    try {
      closeAndDelete(database);
      databasePath = extractAssetToDatabaseDirectory("sqlcipher-4.x-testkey.db");
      database = SQLiteDatabase.openDatabase(databasePath.getPath(), "testkey".getBytes(StandardCharsets.UTF_8), null, SQLiteDatabase.OPEN_READWRITE, null);
      Cursor cursor = database.rawQuery("SELECT * FROM t1;");
      if(cursor != null && cursor.moveToFirst()){
        a = cursor.getInt(0);
        b = cursor.getInt(1);
        cursor.close();
      }
      assertThat(a, is(1));
      assertThat(b, is(2));
    } finally {
      delete(databasePath);
    }
  }

  @Test
  public void openExistingSQLitePlaintextDatabase(){
    File databasePath = null;
    int a = 0, b = 0;
    try {
      closeAndDelete(database);
      databasePath = extractAssetToDatabaseDirectory("sqlite-plaintext.db");
      database = SQLiteDatabase.openDatabase(databasePath.getPath(), "", null, SQLiteDatabase.OPEN_READWRITE, null);
      Cursor cursor = database.rawQuery("SELECT * FROM t1;");
      if(cursor != null && cursor.moveToFirst()){
        a = cursor.getInt(0);
        b = cursor.getInt(1);
        cursor.close();
      }
      assertThat(a, is(1));
      assertThat(b, is(2));
    } finally {
      delete(databasePath);
    }
  }

  @Test
  public void insertDataQueryByObjectParams() {
    float a = 1.25f, a1 = 0.0f;
    double b = 2.00123d, b1 = 0.0d;
    database.execSQL("create table t1(a,b);");
    database.execSQL("insert into t1(a,b) values(?,?)", new Object[]{a, b});
    Cursor cursor = database.rawQuery("select * from t1 where a = ? and b = ?;", a, b);
    if (cursor != null && cursor.moveToFirst()) {
      a1 = cursor.getFloat(0);
      b1 = cursor.getDouble(1);
      cursor.close();
    }
    assertThat(a1, is(a));
    assertThat(b1, is(b));
  }

  @Test
  public void shouldChangeDatabasePasswordSuccessfully() {
    try {
      int a = 3, b = 4;
      int a1 = 0, b1 = 0;
      String originalPassword = "foo", newPassword = "bar";
      database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("foo.db"), originalPassword, null, null);
      database.execSQL("create table t1(a,b);");
      database.execSQL("insert into t1(a,b) values(?,?)", new Object[]{a, b});
      database.changePassword(newPassword);
      database.close();
      database = null;
      try {
        database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("foo.db"), originalPassword, null, null);
        assertThat(database, is(nullValue()));
      } catch (SQLiteException ex) {
        database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("foo.db"), newPassword, null, null);
        Cursor cursor = database.rawQuery("select * from t1;");
        if (cursor != null && cursor.moveToFirst()) {
          a1 = cursor.getInt(0);
          b1 = cursor.getInt(1);
          cursor.close();
        }
        assertThat(a1, is(a));
        assertThat(b1, is(b));
      }
    } finally {
      delete(context.getDatabasePath("foo.db"));
    }
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowExceptionWhenChangingPasswordOnClosedDatabase() {
    try {
      database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("foo.db"), "foo", null, null);
      database.close();
      database.changePassword("bar");
    } finally {
      delete(context.getDatabasePath("foo.db"));
    }
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowExceptionOnChangePasswordWithReadOnlyDatabase() {
    try {
      database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("foo.db").getAbsolutePath(), "foo", null, null);
      database.execSQL("create table t1(a,b);");
      database.close();
      database = null;
      database = SQLiteDatabase.openDatabase(context.getDatabasePath("foo.db").getAbsolutePath(), "foo", null, SQLiteDatabase.OPEN_READONLY, null, null);
      database.changePassword("bar");
    } finally {
      delete(context.getDatabasePath("foo.db"));
    }
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowExceptionOnChangePasswordWithInMemoryDatabase() {
    database = SQLiteDatabase.openOrCreateDatabase(SQLiteDatabaseConfiguration.MEMORY_DB_PATH, "foo", null, null, null);
    database.changePassword("bar");
  }
}
