package net.zetetic.database.sqlcipher;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

public class SupportOpenHelperFactory implements SupportSQLiteOpenHelper.Factory {

  private final byte[] password;
  private final SQLiteDatabaseHook hook;
  private final boolean enableWriteAheadLogging;

  public SupportOpenHelperFactory(byte[] password){
    this(password, null, false);
  }

  public SupportOpenHelperFactory(byte[] password, SQLiteDatabaseHook hook, boolean enableWriteAheadLogging) {
    this.password = password;
    this.hook = hook;
    this.enableWriteAheadLogging = enableWriteAheadLogging;
  }

  @NonNull
  @Override
  public SupportSQLiteOpenHelper create(@NonNull SupportSQLiteOpenHelper.Configuration configuration) {
    return new SupportHelper(configuration, this.password, this.hook, enableWriteAheadLogging);
  }
}
