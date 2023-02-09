package net.zetetic.database.sqlcipher;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

public class SupportOpenHelperFactory implements SupportSQLiteOpenHelper.Factory {

  private static final int UNCHANGED = -1;

  private final byte[] password;
  private final SQLiteDatabaseHook hook;
  private final boolean enableWriteAheadLogging;

  private final int minimumSupportedVersion;

  public SupportOpenHelperFactory(byte[] password){
    this(password, null, false);
  }

  public SupportOpenHelperFactory(byte[] password, SQLiteDatabaseHook hook, boolean enableWriteAheadLogging) {
    this(password, hook, enableWriteAheadLogging, UNCHANGED);
  }

  public SupportOpenHelperFactory(byte[] password, SQLiteDatabaseHook hook,
                                  boolean enableWriteAheadLogging, int minimumSupportedVersion) {
    this.password = password;
    this.hook = hook;
    this.enableWriteAheadLogging = enableWriteAheadLogging;
    this.minimumSupportedVersion = minimumSupportedVersion;
  }

  @NonNull
  @Override
  public SupportSQLiteOpenHelper create(@NonNull SupportSQLiteOpenHelper.Configuration configuration) {
    if (minimumSupportedVersion == UNCHANGED) {
      return new SupportHelper(configuration, this.password, this.hook, enableWriteAheadLogging);
    } else {
      return new SupportHelper(configuration, this.password, this.hook,
              enableWriteAheadLogging, minimumSupportedVersion);
    }
  }
}
