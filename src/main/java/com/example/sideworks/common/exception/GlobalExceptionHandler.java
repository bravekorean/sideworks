package com.example.sideworks.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleConcurrentModification(Exception e) {
        ErrorCode errorCode = ErrorCode.CONCURRENT_MODIFICATION;
        LOG.atWarn().addKeyValue("event", "OPTIMISTIC_LOCK_CONFLICT").log("Concurrent modification rejected");
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    /**
     * 행 잠금 대기 시간 초과(MySQL 1205)는 서버 장애가 아니라 같은 자원을 다른 요청이 처리 중이라는 뜻이다.
     * 재시도할 수 있는 409로 응답하고, 운영 중 흔히 생길 수 있으므로 스택 없이 WARN으로만 남긴다.
     * 트랜잭션은 이미 롤백됐으므로 부분 반영은 없다.
     */
    @ExceptionHandler({
            PessimisticLockingFailureException.class,
            jakarta.persistence.PessimisticLockException.class,
            jakarta.persistence.LockTimeoutException.class
    })
    public ResponseEntity<ErrorResponse> handleLockWaitTimeout(Exception e) {
        ErrorCode errorCode = ErrorCode.LOCK_WAIT_TIMEOUT;
        LOG.atWarn()
                .addKeyValue("event", "LOCK_WAIT_TIMEOUT")
                .addKeyValue("errorCode", errorCode.getCode())
                .addKeyValue("status", errorCode.getStatus().value())
                .addKeyValue("exception", e.getClass().getSimpleName())
                .log("Lock wait timed out");
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        ErrorCode errorCode = ErrorCode.ATTACHMENT_LIMIT_EXCEEDED;

        LOG.atWarn()
                .addKeyValue("event", "ATTACHMENT_SIZE_LIMIT_EXCEEDED")
                .addKeyValue("errorCode", errorCode.getCode())
                .addKeyValue("status", errorCode.getStatus().value())
                .log("Attachment upload size limit exceeded");

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        ErrorCode errorCode = ErrorCode.UNSUPPORTED_MEDIA_TYPE;

        LOG.atWarn()
                .addKeyValue("event", "UNSUPPORTED_MEDIA_TYPE")
                .addKeyValue("errorCode", errorCode.getCode())
                .addKeyValue("status", errorCode.getStatus().value())
                .log("Unsupported request media type");

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

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        LOG.atWarn()
                .addKeyValue("event", "INVALID_REQUEST_HANDLED")
                .addKeyValue("errorCode", errorCode.getCode())
                .addKeyValue("status", errorCode.getStatus().value())
                .log("Invalid request handled");
        return ResponseEntity.badRequest().body(ErrorResponse.from(errorCode));
    }
}
