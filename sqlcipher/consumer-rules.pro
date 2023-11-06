-keep class net.zetetic.** {
  native <methods>;
  private native <methods>;
  public <init>(...);
  long mNativeHandle;
}

-keepclassmembers class net.zetetic.database.sqlcipher.SQLiteCustomFunction {
  public java.lang.String name;
  public int numArgs;
  private void dispatchCallback(java.lang.String[]);
}

-keepclassmembers class net.zetetic.database.sqlcipher.SQLiteDebug$PagerStats {
  public int largestMemAlloc;
  public int memoryUsed;
  public int pageCacheOverflow;
}
