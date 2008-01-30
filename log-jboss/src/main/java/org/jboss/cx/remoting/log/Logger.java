package org.jboss.cx.remoting.log;

/**
 *
 */
public final class Logger {

    @SuppressWarnings ({"NonConstantLogger"})
    private org.jboss.logging.Logger logger;

    private Logger(final String name) {
        logger = org.jboss.logging.Logger.getLogger(name);
    }

    public static Logger getLogger(final String name) {
        return new Logger(name);
    }

    public static Logger getLogger(final Class claxx) {
        return new Logger(claxx.getName());
    }

    public boolean isTrace() {
        return logger.isTraceEnabled();
    }

    public void error(String msg) {
        logger.error(msg);
    }

    public void error(Throwable ex, String msg, Object... params) {
        logger.error(String.format(msg, params), ex);
    }

    public void error(String msg, Object... params) {
        logger.error(String.format(msg, params));
    }

    public void warn(String msg) {
        logger.warn(msg);
    }

    public void warn(Throwable ex, String msg, Object... params) {
        logger.warn(String.format(msg, params), ex);
    }

    public void warn(String msg, Object... params) {
        logger.warn(String.format(msg, params));
    }

    public void info(String msg) {
        logger.info(msg);
    }

    public void info(Throwable ex, String msg, Object... params) {
        logger.info(String.format(msg, params), ex);
    }

    public void info(String msg, Object... params) {
        logger.info(String.format(msg, params));
    }

    public void debug(String msg) {
        logger.debug(msg);
    }

    public void debug(Throwable ex, String msg, Object... params) {
        logger.debug(String.format(msg, params), ex);
    }

    public void debug(String msg, Object... params) {
        logger.debug(String.format(msg, params));
    }

    public void trace(String msg) {
        logger.trace(msg);
    }

    public void trace(Throwable ex, String msg, Object... params) {
        logger.trace(String.format(msg, params), ex);
    }

    public void trace(String msg, Object... params) {
        logger.trace(String.format(msg, params));
    }
}
