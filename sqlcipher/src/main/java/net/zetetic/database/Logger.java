package net.zetetic.database;

public class Logger {

	static {
		setTarget(new LogcatTarget());
	}

	public static final int VERBOSE = 2;
	public static final int DEBUG = 3;
	public static final int INFO = 4;
	public static final int WARN = 5;
	public static final int ERROR = 6;
	public static final int ASSERT = 7;

	private static LogTarget target;

	public static void setTarget(LogTarget target){
		Logger.target = target;
	}

	private static LogTarget getTarget(){
		if(Logger.target == null){
			setTarget(new NoopTarget());
		}
		return Logger.target;
	}

	public static boolean isLoggable(String tag, int priority){
		return getTarget().isLoggable(tag, priority);
	}

	public static void i(String tag, String message) {
		getTarget().log(Logger.INFO, tag, message, null);
	}

	public static void i(String tag, String message, Throwable throwable) {
		getTarget().log(Logger.INFO, tag, message, throwable);
	}

	public static void d(String tag, String message) {
		getTarget().log(Logger.DEBUG, tag, message, null);
	}

	public static void d(String tag, String message, Throwable throwable) {
		getTarget().log(Logger.DEBUG, tag, message, throwable);
	}

	public static void e(String tag, String message) {
		getTarget().log(Logger.ERROR, tag, message, null);
	}

	public static void e(String tag, String message, Throwable throwable) {
		getTarget().log(Logger.ERROR, tag, message, throwable);
	}

	public static void v(String tag, String message) {
		getTarget().log(Logger.VERBOSE, tag, message, null);
	}

	public static void v(String tag, String message, Throwable throwable) {
		getTarget().log(Logger.VERBOSE, tag, message, throwable);
	}

	public static void w(String tag, String message) {
		getTarget().log(Logger.WARN, tag, message, null);
	}

	public static void w(String tag, String message, Throwable throwable) {
		getTarget().log(Logger.WARN, tag, message, throwable);
	}

	public static void wtf(String tag, String message) {
		getTarget().log(Logger.ASSERT, tag, message, null);
	}

	public static void wtf(String tag, String message, Throwable throwable) {
		getTarget().log(Logger.ASSERT, tag, message, throwable);
	}
}