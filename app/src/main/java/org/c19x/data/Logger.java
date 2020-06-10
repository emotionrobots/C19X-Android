package org.c19x.data;

import android.util.Log;

import java.io.PrintStream;

public class Logger {
    private final static short DEBUG = 0;
    private final static short INFO = 1;
    private final static short WARN = 2;
    private final static short ERROR = 3;
    private static short level = DEBUG;

    private static PrintStream stream = null;


    public final static void debug(final String tag, final String message, final Object... values) {
        if (level <= DEBUG) {
            output(DEBUG, tag, message, values);
        }
    }

    public final static void info(final String tag, final String message, final Object... values) {
        if (level <= INFO) {
            output(INFO, tag, message, values);
        }
    }

    public final static void warn(final String tag, final String message, final Object... values) {
        if (level <= WARN) {
            output(WARN, tag, message, values);
        }
    }

    public final static void error(final String tag, final String message, final Object... values) {
        if (level <= ERROR) {
            output(ERROR, tag, message, values);
        }
    }

    private final static void output(final int level, final String tag, final String message, final Object... values) {
        if (stream != null) {
            outputStream(level, tag, message, values);
        } else {
            outputLog(level, tag, message, values);
        }
    }

    private final static void outputStream(final int level, final String tag, final String message, final Object... values) {
        final Throwable throwable = getThrowable(values);
        String label = "DEBUG";
        switch (level) {
            case DEBUG: {
                label = "DEBUG";
                break;
            }
            case INFO: {
                label = "INFO";
                break;
            }
            case WARN: {
                label = "WARN";
                break;
            }
            case ERROR: {
                label = "ERROR";
                break;
            }
        }
        if (throwable == null) {
            stream.println(label + " [" + tag + "] : " + render(message, values));
        } else {
            stream.println(label + " [" + tag + "] : " + render(message, values));
            throwable.printStackTrace(stream);
        }
    }

    private final static void outputLog(final int level, final String tag, final String message, final Object... values) {
        final Throwable throwable = getThrowable(values);
        switch (level) {
            case DEBUG: {
                if (throwable == null) {
                    Log.d(tag, render(message, values));
                } else {
                    Log.d(tag, render(message, values), throwable);
                }
                break;
            }
            case INFO: {
                if (throwable == null) {
                    Log.i(tag, render(message, values));
                } else {
                    Log.i(tag, render(message, values), throwable);
                }
                break;
            }
            case WARN: {
                if (throwable == null) {
                    Log.w(tag, render(message, values));
                } else {
                    Log.w(tag, render(message, values), throwable);
                }
                break;
            }
            case ERROR: {
                if (throwable == null) {
                    Log.e(tag, render(message, values));
                } else {
                    Log.e(tag, render(message, values), throwable);
                }
                break;
            }
        }
    }

    private final static Throwable getThrowable(final Object... values) {
        if (values.length > 0 && values[values.length - 1] instanceof Throwable) {
            return (Throwable) values[values.length - 1];
        } else {
            return null;
        }
    }

    private final static String render(final String message, final Object... values) {
        if (values.length == 0) {
            return message;
        } else {
            final StringBuilder stringBuilder = new StringBuilder();

            int valueIndex = 0;
            int start = 0;
            int end = message.indexOf("{}");
            while (end > 0) {
                stringBuilder.append(message.substring(start, end));
                if (values.length > valueIndex) {
                    if (values[valueIndex] == null) {
                        stringBuilder.append("NULL");
                    } else {
                        stringBuilder.append(values[valueIndex].toString());
                    }
                }
                valueIndex++;
                start = end + 2;
                end = message.indexOf("{}", start);
            }
            stringBuilder.append(message.substring(start));

            return stringBuilder.toString();
        }
    }
}
