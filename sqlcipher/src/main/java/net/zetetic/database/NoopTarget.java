package net.zetetic.database;

public class NoopTarget implements LogTarget {

	@Override
	public boolean isLoggable(String tag, int priority) {
		return false;
	}

	@Override
	public void log(int priority, String tag, String message, Throwable throwable) {}
}
