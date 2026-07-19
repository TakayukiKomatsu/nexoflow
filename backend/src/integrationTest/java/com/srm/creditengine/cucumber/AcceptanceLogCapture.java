package com.srm.creditengine.cucumber;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Context-owned capture of structured operational events for black-box log assertions. */
final class AcceptanceLogCapture implements AutoCloseable {
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    AcceptanceLogCapture() {
        appender.start();
        root.addAppender(appender);
    }

    void clear() {
        appender.list.clear();
    }

    List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
    }
}
