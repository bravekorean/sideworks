package com.example.sideworks.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        LOG.atWarn()
                .addKeyValue("event", "BUSINESS_EXCEPTION_HANDLED")
                .addKeyValue("errorCode", errorCode.getCode())
                .addKeyValue("status", errorCode.getStatus().value())
                .log("Business exception handled");

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        ErrorCode errorCode = ErrorCode.INVALID_LOGIN;

        LOG.atWarn()
                .addKeyValue("event", "ILLEGAL_ARGUMENT_HANDLED")
                .addKeyValue("errorCode", errorCode.getCode())
                .addKeyValue("status", errorCode.getStatus().value())
                .log("Illegal argument handled");

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        LOG.atError()
                .setCause(e)
                .addKeyValue("event", "UNEXPECTED_EXCEPTION_HANDLED")
                .addKeyValue("errorCode", errorCode.getCode())
                .addKeyValue("status", errorCode.getStatus().value())
                .log("Unexpected exception handled");

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }
}
