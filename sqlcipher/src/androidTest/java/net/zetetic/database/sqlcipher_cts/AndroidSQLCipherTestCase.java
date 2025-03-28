package net.zetetic.database.sqlcipher_cts;

import android.content.Context;
import android.icu.text.NumberFormat;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public abstract class AndroidSQLCipherTestCase {

  protected SQLiteDatabase database;
  protected static String DATABASE_NAME = "database_test.db";
  protected String TAG = getClass().getSimpleName();
  protected File databaseFilePath = null;
  protected Context context = null;

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Before
  public void setUp() {
    context = InstrumentationRegistry.getInstrumentation().getContext();
    System.loadLibrary("sqlcipher");
    databaseFilePath = context.getDatabasePath(DATABASE_NAME);
    databaseFilePath.mkdirs();
    if (databaseFilePath.exists()) {
      databaseFilePath.delete();
    }
    database = SQLiteDatabase.openOrCreateDatabase(databaseFilePath, "foo", null, null, null);
  }

  @After
  public void tearDown() {
    if (database != null) {
      database.close();
    }
    if (context != null) {
      context.deleteDatabase(DATABASE_NAME);
    }
  }

  public File extractAssetToDatabaseDirectory(String fileName) {
    File destinationPath = null;
    try {
      int length;
      InputStream sourceDatabase = context.getAssets().open(fileName);
      destinationPath = context.getDatabasePath(fileName);
      OutputStream destination = new FileOutputStream(destinationPath);
      byte[] buffer = new byte[4096];
      while ((length = sourceDatabase.read(buffer)) > 0) {
        destination.write(buffer, 0, length);
      }
      sourceDatabase.close();
      destination.flush();
      destination.close();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    return destinationPath;
  }

  public void delete(File filePath) {
    if (filePath == null) return;
    if (filePath.exists()) {
      boolean result = filePath.delete();
      log("Deleted file:%s result:%s", filePath.getAbsolutePath(), result);
    }
  }

  public void closeAndDelete(SQLiteDatabase database) {
    if (database == null) return;
    File path = new File(database.getPath());
    database.close();
    if (path.exists()) {
      boolean result = path.delete();
      log("Deleted database:%s result:%s", path.getAbsolutePath(), result);
    }
  }

  public byte[] generateRandomBytes(int length) {
    SecureRandom random = new SecureRandom();
    byte[] data = new byte[length];
    random.nextBytes(data);
    return data;
  }

  protected void log(String message, Object... args) {
    Log.i(TAG, String.format(Locale.getDefault(), message, args));
  }

  protected void loge(Exception ex, String message, Object... args) {
    Log.e(TAG, String.format(Locale.getDefault(), message, args), ex);
  }

  public interface RowColumnValueBuilder {
    Object buildRowColumnValue(String[] columns, int row, int column);
  }

  protected void buildDatabase(
    SQLiteDatabase database,
    int rows,
    int columns,
    RowColumnValueBuilder builder) {
    var columnNames = new ArrayList<String>();
    Log.i(TAG, String.format("Building database with %s rows, %d columns",
      NumberFormat.getInstance().format(rows), columns));
    var createTemplate = "CREATE TABLE t1(%s);";
    var insertTemplate = "INSERT INTO t1 VALUES(%s);";
    var createBuilder = new StringBuilder();
    var insertBuilder = new StringBuilder();
    for (int column = 0; column < columns; column++) {
      var columnName = generateColumnName(columnNames, column);
      createBuilder.append(String.format("%s BLOB%s",
        columnName,
        column != columns - 1 ? "," : ""));
      insertBuilder.append(String.format("?%s", column != columns - 1 ? "," : ""));
    }
    var create = String.format(createTemplate, createBuilder.toString());
    var insert = String.format(insertTemplate, insertBuilder.toString());
    database.execSQL("DROP TABLE IF EXISTS t1;");
    database.execSQL(create);
    database.execSQL("BEGIN;");
    var names = columnNames.toArray(new String[0]);
    for (int row = 0; row < rows; row++) {
      var insertArgs = new Object[columns];
      for (var column = 0; column < columns; column++) {
        insertArgs[column] = builder.buildRowColumnValue(names, row, column);
      }
      database.execSQL(insert, insertArgs);
    }
    database.execSQL("COMMIT;");
    Log.i(TAG, String.format("Database built with %d columns, %d rows", columns, rows));
  }

  protected Integer[] generateRandomNumbers(
    int max,
    int times){
    var random = new SecureRandom();
    var numbers = new ArrayList<>();
    for(var index = 0; index < times; index++){
      boolean alreadyExists;
      do {
        var value = random.nextInt(max);
        alreadyExists = numbers.contains(value);
        if(!alreadyExists){
          numbers.add(value);
        }
      } while(alreadyExists);
    }
    return numbers.toArray(new Integer[0]);
  }

  protected List<String> ReservedWords = Arrays.asList(
    "ABORT",
    "ACTION",
    "ADD",
    "AFTER",
    "ALL",
    "ALTER",
    "ANALYZE",
    "AND",
    "AS",
    "ASC",
    "ATTACH",
    "AUTOINCREMENT",
    "BEFORE",
    "BEGIN",
    "BETWEEN",
    "BY",
    "CASCADE",
    "CASE",
    "CAST",
    "CHECK",
    "COLLATE",
    "COLUMN",
    "COMMIT",
    "CONFLICT",
    "CONSTRAINT",
    "CREATE",
    "CROSS",
    "CURRENT_DATE",
    "CURRENT_TIME",
    "CURRENT_TIMESTAMP",
    "DATABASE",
    "DEFAULT",
    "DEFERRABLE",
    "DEFERRED",
    "DELETE",
    "DESC",
    "DETACH",
    "DISTINCT",
    "DROP",
    "EACH",
    "ELSE",
    "END",
    "ESCAPE",
    "EXCEPT",
    "EXCLUSIVE",
    "EXISTS",
    "EXPLAIN",
    "FAIL",
    "FOR",
    "FOREIGN",
    "FROM",
    "FULL",
    "GLOB",
    "GROUP",
    "HAVING",
    "IF",
    "IGNORE",
    "IMMEDIATE",
    "IN",
    "INDEX",
    "INDEXED",
    "INITIALLY",
    "INNER",
    "INSERT",
    "INSTEAD",
    "INTERSECT",
    "INTO",
    "IS",
    "ISNULL",
    "JOIN",
    "KEY",
    "LEFT",
    "LIKE",
    "LIMIT",
    "MATCH",
    "NATURAL",
    "NO",
    "NOT",
    "NOTNULL",
    "NULL",
    "OF",
    "OFFSET",
    "ON",
    "OR",
    "ORDER",
    "OUTER",
    "PLAN",
    "PRAGMA",
    "PRIMARY",
    "QUERY",
    "RAISE",
    "RECURSIVE",
    "REFERENCES",
    "REGEXP",
    "REINDEX",
    "RELEASE",
    "RENAME",
    "REPLACE",
    "RESTRICT",
    "RIGHT",
    "ROLLBACK",
    "ROW",
    "SAVEPOINT",
    "SELECT",
    "SET",
    "TABLE",
    "TEMP",
    "TEMPORARY",
    "THEN",
    "TO",
    "TRANSACTION",
    "TRIGGER",
    "UNION",
    "UNIQUE",
    "UPDATE",
    "USING",
    "VACUUM",
    "VALUES",
    "VIEW",
    "VIRTUAL",
    "WHEN",
    "WHERE",
    "WITH",
    "WITHOUT");

  private String generateColumnName(
    List<String> columnNames,
    int columnIndex){
    var random = new SecureRandom();
    var labels = "abcdefghijklmnopqrstuvwxyz";
    var element = columnIndex < labels.length()
      ? String.valueOf(labels.charAt(columnIndex))
      : String.valueOf(labels.charAt(random.nextInt(labels.length() - 1)));
    while(columnNames.contains(element) || ReservedWords.contains(element.toUpperCase())){
      element += labels.charAt(random.nextInt(labels.length() - 1));
    }
    columnNames.add(element);
    Log.i(TAG, String.format("Generated column name:%s for index:%d", element, columnIndex));
    return element;
  }

}
