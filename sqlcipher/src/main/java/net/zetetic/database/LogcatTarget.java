package net.zetetic.database;

import android.util.Log;

public class LogcatTarget implements LogTarget {

   @Override
   public boolean isLoggable(String tag, int priority) {
      return Log.isLoggable(tag, priority);
   }

   public void log(int priority, String tag, String message, Throwable throwable){
      switch (priority){
         case Logger.VERBOSE -> Log.v(tag, message, throwable);
         case Logger.DEBUG -> Log.d(tag, message, throwable);
         case Logger.INFO -> Log.i(tag, message, throwable);
         case Logger.WARN -> Log.w(tag, message, throwable);
         case Logger.ERROR -> Log.e(tag, message, throwable);
         case Logger.ASSERT -> Log.wtf(tag, message, throwable);
      }
   }
}
