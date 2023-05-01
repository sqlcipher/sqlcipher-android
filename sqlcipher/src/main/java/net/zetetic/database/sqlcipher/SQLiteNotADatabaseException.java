package net.zetetic.database.sqlcipher;

import android.database.sqlite.SQLiteException;

/**
 * An exception that is specific to the "SQLITE_NOTADB" error code.
 *
 * <a href="https://www.sqlite.org/rescode.html#notadb">SQLITE_NOTADB</a>
 */
class SQLiteNotADatabaseException extends SQLiteException {
   public SQLiteNotADatabaseException() {
      super();
   }

   public SQLiteNotADatabaseException(String error) {
      super(error);
   }

   public SQLiteNotADatabaseException(String error, Throwable cause) {
      super(error, cause);
   }
}
