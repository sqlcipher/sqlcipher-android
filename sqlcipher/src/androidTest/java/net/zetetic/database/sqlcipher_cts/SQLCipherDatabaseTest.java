package net.zetetic.database.sqlcipher_cts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteException;

import net.zetetic.database.sqlcipher.SQLiteCursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseConfiguration;
import net.zetetic.database.sqlcipher.SQLiteStatement;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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

  @Test(expected = SQLiteException.class)
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

  @Test(expected = SQLiteException.class)
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
  public void openExistingSQLCipherDatabaseWithStringPassword() {
    File databasePath = null;
    int a = 0, b = 0;
    try {
      closeAndDelete(database);
      databasePath = extractAssetToDatabaseDirectory("sqlcipher-4.x-testkey.db");
      database = SQLiteDatabase.openDatabase(databasePath.getPath(), "testkey", null, SQLiteDatabase.OPEN_READWRITE, null);
      Cursor cursor = database.rawQuery("SELECT * FROM t1;");
      if (cursor != null && cursor.moveToFirst()) {
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
  public void openExistingSQLCipherDatabaseWithByteArrayPassword() {
    File databasePath = null;
    int a = 0, b = 0;
    try {
      closeAndDelete(database);
      databasePath = extractAssetToDatabaseDirectory("sqlcipher-4.x-testkey.db");
      database = SQLiteDatabase.openDatabase(databasePath.getPath(), "testkey".getBytes(StandardCharsets.UTF_8), null, SQLiteDatabase.OPEN_READWRITE, null);
      Cursor cursor = database.rawQuery("SELECT * FROM t1;");
      if (cursor != null && cursor.moveToFirst()) {
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
  public void openExistingSQLitePlaintextDatabase() {
    File databasePath = null;
    int a = 0, b = 0;
    try {
      closeAndDelete(database);
      databasePath = extractAssetToDatabaseDirectory("sqlite-plaintext.db");
      database = SQLiteDatabase.openDatabase(databasePath.getPath(), "", null, SQLiteDatabase.OPEN_READWRITE, null);
      Cursor cursor = database.rawQuery("SELECT * FROM t1;");
      if (cursor != null && cursor.moveToFirst()) {
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

  @Test
  public void shouldPerformRawQueryWithBoolean() {
    boolean a = false, b = true;
    database.execSQL("create table t1(a,b);");
    database.execSQL("insert into t1(a,b) values(?, ?);", new Object[]{true, false});
    Cursor cursor = database.rawQuery("select * from t1 where b = ?;", false);
    if (cursor != null && cursor.moveToFirst()) {
      a = cursor.getInt(0) > 0;
      b = cursor.getInt(1) > 0;
    }
    assertThat(a, is(true));
    assertThat(b, is(false));
  }

  @Test
  public void shouldPerformRawQueryWithByteArray() {
    byte[] a = generateRandomBytes(64);
    byte[] b = generateRandomBytes(64);
    byte[] aActual = null, bActual = null;
    database.execSQL("create table t1(a,b);");
    database.execSQL("insert into t1(a,b) values(?, ?);", new Object[]{a, b});
    Cursor cursor = database.rawQuery("select * from t1 where b = ?;", b);
    if (cursor != null && cursor.moveToFirst()) {
      aActual = cursor.getBlob(0);
      bActual = cursor.getBlob(1);
    }
    assertThat(aActual, is(a));
    assertThat(bActual, is(b));
  }

  @Test
  public void shouldPerformRawQueryWithDouble() {
    double a = 3.14d, b = 42.0d;
    double aActual = 0.0d, bActual = 0.0d;
    database.execSQL("create table t1(a,b);");
    database.execSQL("insert into t1(a,b) values(?, ?);", new Object[]{a, b});
    Cursor cursor = database.rawQuery("select * from t1 where b = ?;", b);
    if (cursor != null && cursor.moveToFirst()) {
      aActual = cursor.getDouble(0);
      bActual = cursor.getDouble(1);
    }
    assertThat(aActual, is(a));
    assertThat(bActual, is(b));
  }

  @Test
  public void shouldPerformRawQueryWithFloat() {
    float a = 3.14f, b = 42.0f;
    float aActual = 0.0f, bActual = 0.0f;
    database.execSQL("create table t1(a,b);");
    database.execSQL("insert into t1(a,b) values(?, ?);", new Object[]{a, b});
    Cursor cursor = database.rawQuery("select * from t1 where b = ?;", b);
    if (cursor != null && cursor.moveToFirst()) {
      aActual = cursor.getFloat(0);
      bActual = cursor.getFloat(1);
    }
    assertThat(aActual, is(a));
    assertThat(bActual, is(b));
  }

  @Test
  public void shouldPerformRawQueryWithLong() {
    long a = 3L, b = 42L;
    long aActual = 0L, bActual = 0L;
    database.execSQL("create table t1(a,b);");
    database.execSQL("insert into t1(a,b) values(?, ?);", new Object[]{a, b});
    Cursor cursor = database.rawQuery("select * from t1 where b = ?;", b);
    if (cursor != null && cursor.moveToFirst()) {
      aActual = cursor.getLong(0);
      bActual = cursor.getLong(1);
    }
    assertThat(aActual, is(a));
    assertThat(bActual, is(b));
  }

  @Test
  public void shouldPerformRawQueryWithString() {
    String a = "one for the money", b = "two for the show";
    String aActual = "", bActual = "";
    database.execSQL("create table t1(a,b);");
    database.execSQL("insert into t1(a,b) values(?, ?);", new Object[]{a, b});
    Cursor cursor = database.rawQuery("select * from t1 where b = ?;", b);
    if (cursor != null && cursor.moveToFirst()) {
      aActual = cursor.getString(0);
      bActual = cursor.getString(1);
    }
    assertThat(aActual, is(a));
    assertThat(bActual, is(b));
  }

  @Test
  public void shouldPerformFTS5Search() {
    boolean found = false;
    database.execSQL("CREATE VIRTUAL TABLE email USING fts5(sender, title, body);");
    database.execSQL("insert into email(sender, title, body) values(?, ?, ?);",
        new Object[]{"foo@bar.com", "Test Email", "This is a test email message."});
    Cursor cursor = database.rawQuery("select * from email where email match ?;", "test");
    if (cursor != null && cursor.moveToFirst()) {
      found = cursor.getString(cursor.getColumnIndex("sender")).equals("foo@bar.com");
    }
    assertThat(found, is(true));
  }

  @Test
  public void shouldPerformRTreeTest() {
    int id = 0;
    String create = "CREATE VIRTUAL TABLE demo_index USING rtree(id, minX, maxX, minY, maxY);";
    String insert = "INSERT INTO demo_index VALUES(?, ?, ?, ?, ?);";
    database.execSQL(create);
    database.execSQL(insert, new Object[]{1, -80.7749, -80.7747, 35.3776, 35.3778});
    Cursor cursor = database.rawQuery("SELECT * FROM demo_index WHERE maxY < ?;", 36);
    if (cursor != null && cursor.moveToNext()) {
      id = cursor.getInt(0);
      cursor.close();
    }
    assertThat(id, is(1));
  }

  @Test
  public void shouldPerformSoundexTest() {
    String value = "";
    Cursor cursor = database.rawQuery("SELECT soundex('sqlcipher');");
    if (cursor != null && cursor.moveToFirst()) {
      value = cursor.getString(0);
      cursor.close();
    }
    assertThat(value, is("S421"));
  }

  @Test
  public void shouldInsertWithOnConflictTest(){
    database.execSQL("create table user(_id integer primary key autoincrement, email text unique not null);");
    ContentValues values = new ContentValues();
    values.put("email", "foo@bar.com");
    long id = database.insertWithOnConflict("user", null, values,
        SQLiteDatabase.CONFLICT_IGNORE);
    long error = database.insertWithOnConflict("user", null, values,
        SQLiteDatabase.CONFLICT_IGNORE);
    assertThat(id, is(1L));
    assertThat(error, is(-1L));
  }

  @Test
  public void shouldPerformRollbackToSavepoint(){
    database.rawExecSQL("savepoint foo;");
    database.rawExecSQL("create table t1(a,b);");
    database.rawExecSQL("insert into t1(a,b) values(?,?);", "one for the money", "two for the show");
    database.rawExecSQL("savepoint bar;");
    database.rawExecSQL("insert into t1(a,b) values(?,?);", "three to get ready", "go man go");
    database.rawExecSQL("rollback transaction to bar;");
    database.rawExecSQL("commit;");
    SQLiteStatement statement = database.compileStatement("select count(*) from t1 where a = ?;");
    statement.bindString(1, "one for the money");
    long count = statement.simpleQueryForLong();
    assertThat(count, is(1L));
  }

  @Test
  public void shouldNotThrowExceptionWithSelectStatementUsingRawExecSQL(){
    database.rawExecSQL("SELECT count(*) FROM sqlite_schema;");
  }

  @Test
  public void shouldPerformInsertUsingRawExecSQL(){
    int a = 0, b = 0;
    database.execSQL("create table t1(a,b);");
    database.rawExecSQL("insert into t1(a,b) values(?, ?);", 1, 2);
    Cursor cursor = database.rawQuery("select * from t1;");
    if(cursor != null && cursor.moveToFirst()){
      a = cursor.getInt(0);
      b = cursor.getInt(1);
      cursor.close();
    }
    assertThat(a, is(1));
    assertThat(b, is(2));
  }

  @Test
  public void shouldPerformUpdateUsingRawExecSQL(){
    int a = 0, b = 0, c = 0;
    database.execSQL("create table t1(a,b,c);");
    database.execSQL("insert into t1(a,b,c) values(?, ?, ?);", new Object[]{1, 2, 3});
    database.rawExecSQL("update t1 set b = ?, c = ? where a = ?", 4, 5, 1);
    Cursor cursor = database.rawQuery("select * from t1;");
    if(cursor != null && cursor.moveToFirst()){
      a = cursor.getInt(0);
      b = cursor.getInt(1);
      c = cursor.getInt(2);
      cursor.close();
    }
    assertThat(a, is(1));
    assertThat(b, is(4));
    assertThat(c, is(5));
  }

  @Test
  public void shouldDeleteDataUsingRawExecSQL(){
    database.execSQL("create table t1(a,b,c);");
    database.execSQL("insert into t1(a,b,c) values(?, ?, ?);", new Object[]{1, 2, 3});
    SQLiteStatement statement = database.compileStatement("select count(*) from t1;");
    assertThat(statement.simpleQueryForLong(), is(1L));
    database.rawExecSQL("delete from t1;");
    assertThat(statement.simpleQueryForLong(), is(0L));
  }

  @Test(expected = IllegalStateException.class)
  public void shouldReportErrorAfterDatabaseCloseWhenCheckingTransactionState(){
    database.close();
    database.inTransaction();
  }

  @Test
  public void shouldRetrieveLargeSingleRowResultFromCursor(){
    try {
      int id = 1;
      byte[] queriedData = null;
      int size = 256;
      SQLiteCursor.setCursorWindowSize(size);
      byte[] data = generateRandomBytes(size);
      database.execSQL("create table t1(a,b);");
      database.execSQL("insert into t1(a,b) values(?,?);", new Object[]{id, data});
      Cursor cursor = database.rawQuery("select b from t1 where a = ?;", new Object[]{id});
      if(cursor != null && cursor.moveToFirst()){
        queriedData = cursor.getBlob(0);
        cursor.close();
      }
      assertThat(Arrays.equals(queriedData, data), is(true));
    } finally {
      SQLiteCursor.resetCursorWindowSize();
    }
  }

  @Test
  public void shouldAllowCursorWindowToResize(){
    try {
      Cursor cursor;
      int id = 1, extra = 1024, size = 256;
      byte[] tooLargeQueriedData = null;
      SQLiteCursor.setCursorWindowSize(size);
      byte[] tooLargeData = generateRandomBytes(size + extra);
      database.execSQL("create table t1(a,b);");
      database.execSQL("insert into t1(a,b) values(?,?);", new Object[]{id, tooLargeData});
      try {
        cursor = database.rawQuery("select b from t1 where a = ?;", new Object[]{id});
        if(cursor != null && cursor.moveToFirst()) {
          fail("CursorWindow should be too small to fill query results");
        }
      } catch (Exception ex){
        SQLiteCursor.setCursorWindowSize(size + extra);
        cursor = database.rawQuery("select b from t1 where a = ?;", new Object[]{id});
        if(cursor != null && cursor.moveToFirst()) {
          tooLargeQueriedData = cursor.getBlob(0);
          cursor.close();
        }
      }
      assertThat(Arrays.equals(tooLargeQueriedData, tooLargeData), is(true));
    } finally {
      SQLiteCursor.resetCursorWindowSize();
    }
  }
}
