package net.zetetic.database.sqlcipher;

public interface SQLiteDatabaseHook {
  /**
   * Called immediately before keying the database.
   */
  void preKey(SQLiteConnection connection);
  /**
   * Called immediately after keying the database.
   */
  void postKey(SQLiteConnection connection);
}
