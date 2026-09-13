package com.example.sideworks.common.logging;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    public static final String HANDLER_ATTRIBUTE = HttpRequestLoggingFilter.class.getName() + ".handler";

    private static final String REQUEST_ID = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger HTTP_LOG = LoggerFactory.getLogger("HTTP");
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain filterChain) throws ServletException, IOException {

        String requestId = createRequestId();
        long startedAt = System.nanoTime();

        MDC.put(REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = calculateDurationMs(startedAt);
            try {
                writeCompletionLog(request, response, durationMs);
            } finally {
                MDC.remove(REQUEST_ID);
            }
        }
    }

    private void writeCompletionLog(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        String route = resolveRoute(request);
        String handler = resolveHandler(request);

        HTTP_LOG.atInfo()
                .addKeyValue("event", "HTTP_REQUEST_COMPLETED")
                .addKeyValue("httpMethod", request.getMethod())
                .addKeyValue("route", route)
                .addKeyValue("handler", handler)
                .addKeyValue("status", response.getStatus())
                .addKeyValue("durationMs", durationMs)
                .log("HTTP request completed");

        writeAccessDeniedAuditLog(request, response, route, handler);
    }

    private void writeAccessDeniedAuditLog(HttpServletRequest request, HttpServletResponse response, String route, String handler) {
        int status = response.getStatus();

        if (status != HttpServletResponse.SC_UNAUTHORIZED && status != HttpServletResponse.SC_FORBIDDEN) {
            return;
        }

        String event = status == HttpServletResponse.SC_UNAUTHORIZED
                ? "AUTHENTICATION_REQUIRED"
                : "ACCESS_DENIED";

        AUDIT_LOG.atWarn()
                .addKeyValue("event", event)
                .addKeyValue("httpMethod", request.getMethod())
                .addKeyValue("route", route)
                .addKeyValue("handler", handler)
                .addKeyValue("status", status)
                .addKeyValue("result", "DENIED")
                .log("HTTP access denied");
    }

    private String resolveRoute(HttpServletRequest request) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

        if (route instanceof String routePattern) {
            return routePattern;
        }

        // Security Filter에서 차단되면 Spring MVC가 템플릿 경로를 결정하지 못한다.
        return request.getRequestURI();
    }

    private String resolveHandler(HttpServletRequest request) {
        Object handler = request.getAttribute(HANDLER_ATTRIBUTE);

        if (handler instanceof String handlerName) {
            return handlerName;
        }

        return "UNRESOLVED";
    }

    private String createRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private long calculateDurationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }
}
