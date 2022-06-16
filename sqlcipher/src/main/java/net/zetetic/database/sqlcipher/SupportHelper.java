package net.zetetic.database.sqlcipher;

import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

public class SupportHelper implements SupportSQLiteOpenHelper {

  private SQLiteOpenHelper openHelper;

  public SupportHelper(final Configuration configuration, byte[] password, SQLiteDatabaseHook hook,
                       boolean enableWriteAheadLogging) {
    openHelper = new SQLiteOpenHelper(configuration.context, configuration.name, password,
        null, configuration.callback.version, configuration.callback.version,
        null, hook, enableWriteAheadLogging) {
      @Override
      public void onCreate(SQLiteDatabase db) {
        configuration.callback.onCreate(db);
      }

      @Override
      public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        configuration.callback.onUpgrade(db, oldVersion, newVersion);
      }
    };
  }

  @Nullable
  @Override
  public String getDatabaseName() {
    return openHelper.getDatabaseName();
  }

  @Override
  public void setWriteAheadLoggingEnabled(boolean enabled) {
    openHelper.setWriteAheadLoggingEnabled(enabled);
  }

  @Override
  public SupportSQLiteDatabase getWritableDatabase() {
    return openHelper.getWritableDatabase();
  }

  @Override
  public SupportSQLiteDatabase getReadableDatabase() {
    return openHelper.getReadableDatabase();
  }

  @Override
  public void close() {
    openHelper.close();
  }
}
