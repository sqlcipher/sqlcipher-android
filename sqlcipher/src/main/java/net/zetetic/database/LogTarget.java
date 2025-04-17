package net.zetetic.database;

public interface LogTarget {
	boolean isLoggable (String tag, int priority);
	void log(int priority, String tag, String message, Throwable throwable);
}