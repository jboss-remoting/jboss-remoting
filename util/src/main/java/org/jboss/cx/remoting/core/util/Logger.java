package org.jboss.cx.remoting.core.util;

import java.util.logging.LogRecord;


/**
 *
 */
public final class Logger {
    public static final class Level extends java.util.logging.Level {
        protected Level(final String name, final int value) {
            super(name, value);
        }
    }

    public static final Level VERBOSE = new Level("VERBOSE", 300);
    public static final Level TRACE = new Level("TRACE", 400);
    public static final Level DEBUG = new Level("DEBUG", 500);
    public static final Level INFO = new Level("INFO", 800);
    public static final Level WARN = new Level("WARN", 900);
    public static final Level ERROR = new Level("ERROR", 1000);

    @SuppressWarnings ({"NonConstantLogger"})
    private final java.util.logging.Logger logger;
    private final String name;

    private Logger(final String name) {
        this.name = name;
        logger = java.util.logging.Logger.getLogger(name);
    }

    public static Logger getLogger(final String name) {
        return new Logger(name);
    }

    public static Logger getLogger(final Class claxx) {
        return new Logger(claxx.getName());
    }

    public boolean isVerbose() {
        return logger.isLoggable(VERBOSE);
    }

    public boolean isTrace() {
        return logger.isLoggable(TRACE);
    }

    private void doLog(Level level, String msg, Throwable ex, Object[] params) {
        LogRecord record = new LogRecord(level, msg);
        record.setLoggerName(name);
        if (ex != null) record.setThrown(ex);
        if (params != null) record.setParameters(params);
        record.setSourceMethodName("");
        record.setSourceClassName("");
        logger.log(record);
    }

    public void error(String msg) {
        doLog(ERROR, msg, null, null);
    }

    public void error(String msg, Throwable ex) {
        doLog(ERROR, msg, ex, null);
    }

    public void error(String msg, Object... params) {
        doLog(ERROR, msg, null, params);
    }

    public void warn(String msg) {
        doLog(WARN, msg, null, null);
    }

    public void warn(String msg, Throwable ex) {
        doLog(WARN, msg, ex, null);
    }

    public void warn(String msg, Object... params) {
        doLog(WARN, msg, null, params);
    }

    public void info(String msg) {
        doLog(INFO, msg, null, null);
    }

    public void info(String msg, Throwable ex) {
        doLog(INFO, msg, ex, null);
    }

    public void info(String msg, Object... params) {
        doLog(INFO, msg, null, params);
    }

    public void debug(String msg) {
        doLog(DEBUG, msg, null, null);
    }

    public void debug(String msg, Throwable ex) {
        doLog(DEBUG, msg, ex, null);
    }

    public void debug(String msg, Object... params) {
        doLog(DEBUG, msg, null, params);
    }

    public void trace(String msg) {
        doLog(TRACE, msg, null, null);
    }

    public void trace(String msg, Throwable ex) {
        doLog(TRACE, msg, ex, null);
    }

    public void trace(String msg, Object... params) {
        doLog(TRACE, msg, null, params);
    }

    public void verbose(String msg) {
        doLog(VERBOSE, msg, null, null);
    }

    public void verbose(String msg, Throwable ex) {
        doLog(VERBOSE, msg, ex, null);
    }

    public void verbose(String msg, Object... params) {
        doLog(VERBOSE, msg, null, params);
    }

}
