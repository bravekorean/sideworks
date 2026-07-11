package com.example.sideworks.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_APPROVER(HttpStatus.BAD_REQUEST, "INVALID_APPROVER", "결재자로 지정할 수 없는 사용자입니다."),
    INVALID_CC_USER(HttpStatus.BAD_REQUEST, "INVALID_CC_USER", "참조자로 지정할 수 없는 사용자입니다."),

    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "INVALID_LOGIN", "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND", "부서를 찾을 수 없습니다."),
    DEPARTMENT_IN_USE(HttpStatus.CONFLICT, "DEPARTMENT_IN_USE", "하위 부서 또는 소속 사용자가 있어 삭제할 수 없습니다."),
    POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "POSITION_NOT_FOUND", "직급을 찾을 수 없습니다."),
    APPROVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND", "결재 문서를 찾을 수 없습니다."),
    APPROVAL_NOT_EDITABLE(HttpStatus.CONFLICT, "APPROVAL_NOT_EDITABLE", "임시저장 상태의 문서만 수정하거나 삭제할 수 있습니다."),
    POSITION_IN_USE(HttpStatus.CONFLICT, "POSITION_IN_USE", "해당 직급을 사용하는 사용자가 있어 삭제할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
