package net.zetetic.database.sqlcipher;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

public class SupportOpenHelperFactory implements SupportSQLiteOpenHelper.Factory {

  private final byte[] password;
  private final SQLiteDatabaseHook hook;

  public SupportOpenHelperFactory(byte[] password){
    this(password, null);
  }

  public SupportOpenHelperFactory(byte[] password, SQLiteDatabaseHook hook){
    this.password = password;
    this.hook = hook;
  }

  @NonNull
  @Override
  public SupportSQLiteOpenHelper create(@NonNull SupportSQLiteOpenHelper.Configuration configuration) {
    return new SupportHelper(configuration, this.password, this.hook);
  }
}
