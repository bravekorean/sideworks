package com.example.sideworks.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HttpRequestLoggingFilterTest {

    private Logger httpLogger;
    private Logger auditLogger;
    private ListAppender<ILoggingEvent> httpListAppender;
    private ListAppender<ILoggingEvent> auditListAppender;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        httpLogger = (Logger) LoggerFactory.getLogger("HTTP");
        auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");

        httpListAppender = createAndAttachListAppender(httpLogger);
        auditListAppender = createAndAttachListAppender(auditLogger);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .addInterceptors(new HandlerLoggingInterceptor())
                .addFilters(new HttpRequestLoggingFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        httpLogger.detachAppender(httpListAppender);
        auditLogger.detachAppender(auditListAppender);
        httpListAppender.stop();
        auditListAppender.stop();
        MDC.remove("requestId");
    }

    @Test
    void API_요청이_완료되면_요청_처리_정보를_기록한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test/123"))
                .andExpect(status().isOk())
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");

        assertThat(requestId)
                .isNotBlank()
                .matches("[0-9a-f]{32}");

        assertThat(httpListAppender.list).hasSize(1);

        ILoggingEvent loggingEvent = httpListAppender.list.getFirst();
        Map<String, Object> keyValues = toKeyValueMap(loggingEvent);

        assertThat(keyValues)
                .containsEntry("event", "HTTP_REQUEST_COMPLETED")
                .containsEntry("httpMethod", "GET")
                .containsEntry("route", "/api/test/{id}")
                .containsEntry("handler", "TestController.handle")
                .containsEntry("status", 200);

        assertThat((Long) keyValues.get("durationMs"))
                .isGreaterThanOrEqualTo(0L);

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void API가_아닌_요청은_요청_로그를_기록하지_않는다() throws Exception {
        mockMvc.perform(get("/test/123"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Request-Id"));

        assertThat(httpListAppender.list).isEmpty();
        assertThat(auditListAppender.list).isEmpty();
    }

    @Test
    void 요청_처리에서_예외가_발생해도_MDC를_정리한다() {
        assertThatThrownBy(() -> mockMvc.perform(get("/api/test/failure")))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(MDC.get("requestId")).isNull();
        assertThat(httpListAppender.list).hasSize(1);
    }

    @Test
    void 접근이_거부되면_Audit_로그를_기록한다() throws Exception {
        mockMvc.perform(get("/api/test/forbidden"))
                .andExpect(status().isForbidden());

        assertThat(auditListAppender.list).hasSize(1);

        Map<String, Object> keyValues = toKeyValueMap(auditListAppender.list.getFirst());

        assertThat(keyValues)
                .containsEntry("event", "ACCESS_DENIED")
                .containsEntry("httpMethod", "GET")
                .containsEntry("route", "/api/test/forbidden")
                .containsEntry("handler", "TestController.forbidden")
                .containsEntry("status", 403)
                .containsEntry("result", "DENIED");
    }

    private ListAppender<ILoggingEvent> createAndAttachListAppender(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private Map<String, Object> toKeyValueMap(ILoggingEvent loggingEvent) {
        return loggingEvent.getKeyValuePairs()
                .stream()
                .collect(Collectors.toMap(
                        keyValue -> keyValue.key,
                        keyValue -> keyValue.value
                ));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/test/{id}")
        String handle(@PathVariable Long id) {
            return "test-" + id;
        }

        @GetMapping("/test/{id}")
        String handleNonApi(@PathVariable Long id) {
            return "test-" + id;
        }

        @GetMapping("/api/test/failure")
        String fail() {
            throw new IllegalStateException("test failure");
        }

        @ResponseStatus(HttpStatus.FORBIDDEN)
        @GetMapping("/api/test/forbidden")
        void forbidden() {
        }
    }
}
