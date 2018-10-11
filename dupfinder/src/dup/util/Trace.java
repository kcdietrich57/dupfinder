package dup.util;

public class Trace {
	public static final int NONE = 0;
	public static final int NORMAL = 1;
	public static final int DEBUG = 2;
	public static final int VERBOSE = 3;

	public static final int traceLevel = NORMAL;

	public static void trace(int level, String msg) {
		if (level <= traceLevel) {
			System.out.print(msg);
			System.out.flush();
		}
	}

	public static void traceln(int level, String msg) {
		trace(level, msg);
		traceln(level);
	}

	public static void traceln(int level) {
		if (level <= traceLevel) {
			System.out.println();
			System.out.flush();
		}
	}

	public static void trace(String msg) {
		trace(NORMAL, msg);
	}

	public static void traceln(String msg) {
		traceln(NORMAL, msg);
	}

	public static void traceln() {
		traceln(NORMAL);
	}
}
