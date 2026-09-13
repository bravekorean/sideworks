package com.example.sideworks.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    void 비즈니스_예외는_스택_트레이스_없이_WARN으로_기록한다() {
        exceptionHandler.handleBusinessException(new BusinessException(ErrorCode.USER_NOT_FOUND));

        assertThat(listAppender.list).hasSize(1);

        ILoggingEvent loggingEvent = listAppender.list.getFirst();
        Map<String, Object> keyValues = toKeyValueMap(loggingEvent);

        assertThat(loggingEvent.getLevel()).isEqualTo(Level.WARN);
        assertThat(loggingEvent.getThrowableProxy()).isNull();
        assertThat(keyValues)
                .containsEntry("event", "BUSINESS_EXCEPTION_HANDLED")
                .containsEntry("errorCode", "USER_NOT_FOUND")
                .containsEntry("status", 404);
    }

    @Test
    void 예상하지_못한_예외는_스택_트레이스와_ERROR로_기록한다() {
        IllegalStateException exception = new IllegalStateException("sensitive test detail");

        exceptionHandler.handleException(exception);

        assertThat(listAppender.list).hasSize(1);

        ILoggingEvent loggingEvent = listAppender.list.getFirst();
        Map<String, Object> keyValues = toKeyValueMap(loggingEvent);

        assertThat(loggingEvent.getLevel()).isEqualTo(Level.ERROR);
        assertThat(loggingEvent.getThrowableProxy()).isNotNull();
        assertThat(keyValues)
                .containsEntry("event", "UNEXPECTED_EXCEPTION_HANDLED")
                .containsEntry("errorCode", "INTERNAL_SERVER_ERROR")
                .containsEntry("status", 500);
    }

    private Map<String, Object> toKeyValueMap(ILoggingEvent loggingEvent) {
        return loggingEvent.getKeyValuePairs()
                .stream()
                .collect(Collectors.toMap(
                        keyValue -> keyValue.key,
                        keyValue -> keyValue.value
                ));
    }

    @Test
    void 첨부파일_요청_제한을_초과하면_413으로_응답한다() {
        var response = exceptionHandler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(20 * 1024 * 1024L)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.ATTACHMENT_LIMIT_EXCEEDED.getCode());
    }

    @Test
    void 지원하지_않는_Content_Type은_415로_응답한다() {
        var response = exceptionHandler.handleHttpMediaTypeNotSupportedException(
                new HttpMediaTypeNotSupportedException("application/json")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode());
    }
}
