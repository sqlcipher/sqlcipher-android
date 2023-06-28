package net.zetetic.database.sqlcipher;

import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

public class SupportHelper implements SupportSQLiteOpenHelper {

  private SQLiteOpenHelper openHelper;

  public SupportHelper(final Configuration configuration, byte[] password, SQLiteDatabaseHook hook,
                       boolean enableWriteAheadLogging) {
    this(configuration, password, hook, enableWriteAheadLogging, 0);
  }

  public SupportHelper(final Configuration configuration, byte[] password, SQLiteDatabaseHook hook,
                       boolean enableWriteAheadLogging, int minimumSupportedVersion) {
    openHelper = new SQLiteOpenHelper(configuration.context, configuration.name, password,
        null, configuration.callback.version, minimumSupportedVersion, null, hook, enableWriteAheadLogging) {
      @Override
      public void onCreate(SQLiteDatabase db) {
        configuration.callback.onCreate(db);
      }

      @Override
      public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        configuration.callback.onUpgrade(db, oldVersion, newVersion);
      }

      @Override
      public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        configuration.callback.onDowngrade(db, oldVersion, newVersion);
      }

      @Override
      public void onOpen(SQLiteDatabase db) {
        configuration.callback.onOpen(db);
      }

      @Override
      public void onConfigure(SQLiteDatabase db) {
        configuration.callback.onConfigure(db);
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
