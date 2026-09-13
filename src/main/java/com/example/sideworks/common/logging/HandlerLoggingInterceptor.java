package com.example.sideworks.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class HandlerLoggingInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            String handlerName = createHandlerName(handlerMethod);

            request.setAttribute(HttpRequestLoggingFilter.HANDLER_ATTRIBUTE, handlerName);
        }

        return true;
    }

    private String createHandlerName(HandlerMethod handlerMethod) {
        String controllerName = handlerMethod.getBeanType().getSimpleName();

        String methodName = handlerMethod.getMethod().getName();

        return controllerName + "." + methodName;
    }
}