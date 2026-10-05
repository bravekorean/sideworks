package com.example.sideworks.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "결재선 템플릿을 찾을 수 없습니다."),
    TEMPLATE_FORBIDDEN(HttpStatus.FORBIDDEN, "TEMPLATE_FORBIDDEN", "결재선 템플릿에 접근할 권한이 없습니다."),
    TEMPLATE_STALE(HttpStatus.CONFLICT, "TEMPLATE_STALE", "템플릿이 변경되었습니다. 다시 조회하거나 수동 결재선으로 전환해주세요."),
    TEMPLATE_BLOCKED(HttpStatus.CONFLICT, "TEMPLATE_BLOCKED", "인사정보가 변경된 템플릿입니다. 관리자가 수정하기 전에는 사용할 수 없습니다."),
    TEMPLATE_INACTIVE(HttpStatus.CONFLICT, "TEMPLATE_INACTIVE", "비활성 결재선 템플릿입니다."),
    TEMPLATE_INVALID_MEMBERS(HttpStatus.BAD_REQUEST, "TEMPLATE_INVALID_MEMBERS", "결재자·참조자 구성 또는 계정 상태를 확인해주세요."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "다른 요청에서 데이터가 변경됐습니다. 새로 조회한 뒤 다시 시도해주세요."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."),
    LEAVE_HIRE_DATE_REQUIRED(HttpStatus.CONFLICT, "LEAVE_HIRE_DATE_REQUIRED", "입사일이 없어 연차를 부여할 수 없습니다. 인사 관리자에게 입사일 등록을 요청해주세요."),
    LEAVE_YEAR_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST, "LEAVE_YEAR_NOT_ELIGIBLE", "입사 전 연도이거나 조회할 수 없는 연도입니다."),
    LEAVE_INVALID_DATE(HttpStatus.BAD_REQUEST, "LEAVE_INVALID_DATE", "휴가 날짜와 단위를 확인해주세요. 미래 근무일 및 동일 연도만 신청할 수 있습니다."),
    LEAVE_NOT_WORKING_DAY(HttpStatus.CONFLICT, "LEAVE_NOT_WORKING_DAY", "신청 날짜에 근무일이 없습니다."),
    LEAVE_INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "LEAVE_INSUFFICIENT_BALANCE", "신청 가능한 연차가 부족합니다."),
    LEAVE_DATE_CONFLICT(HttpStatus.CONFLICT, "LEAVE_DATE_CONFLICT", "신청 날짜 또는 시간대에 다른 휴가가 있습니다."),
    LEAVE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "LEAVE_REQUEST_NOT_FOUND", "휴가 신청을 찾을 수 없습니다."),
    LEAVE_CANCELLATION_PENDING(HttpStatus.CONFLICT, "LEAVE_CANCELLATION_PENDING", "이미 진행 중인 휴가 취소 신청이 있습니다."),
    LEAVE_CANCELLATION_NOT_ALLOWED(HttpStatus.CONFLICT, "LEAVE_CANCELLATION_NOT_ALLOWED", "휴가 시작 전 승인된 휴가만 취소할 수 있습니다."),
    DOCUMENT_TYPE_DUPLICATE(HttpStatus.CONFLICT, "DOCUMENT_TYPE_DUPLICATE", "이미 사용 중인 문서 코드 또는 이름입니다."),
    DOCUMENT_TYPE_STALE(HttpStatus.CONFLICT, "DOCUMENT_TYPE_STALE", "다른 관리자가 수정했습니다. 목록을 새로 조회한 뒤 다시 수정해주세요."),
    DOCUMENT_TYPE_SYSTEM_PROTECTED(HttpStatus.BAD_REQUEST, "DOCUMENT_TYPE_SYSTEM_PROTECTED", "시스템 문서 종류는 수정할 수 없습니다."),
    DOCUMENT_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "DOCUMENT_TYPE_NOT_FOUND", "문서 종류를 찾을 수 없습니다."),
    DOCUMENT_TYPE_INACTIVE(HttpStatus.CONFLICT, "DOCUMENT_TYPE_INACTIVE", "비활성 문서 종류입니다. 다른 종류를 선택해주세요."),
    DOCUMENT_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "DOCUMENT_TYPE_NOT_ALLOWED", "해당 문서 종류는 전용 신청 절차를 이용해주세요."),

    ATTENDANCE_CHANGE_FORBIDDEN(HttpStatus.FORBIDDEN, "ATTENDANCE_CHANGE_FORBIDDEN", "근태 변경 권한이 없습니다."),
    ATTENDANCE_CORRECTION_PENDING(HttpStatus.CONFLICT, "ATTENDANCE_CORRECTION_PENDING", "해당 날짜에 진행 중인 정정 신청이 있습니다."),
    ATTENDANCE_CORRECTION_STALE(HttpStatus.CONFLICT, "ATTENDANCE_CORRECTION_STALE", "신청 이후 원본 근태가 변경되었습니다. 기존 신청을 취소하고 다시 신청해주세요."),
    SCHEDULE_EXCEPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_EXCEPTION_NOT_FOUND", "날짜별 근무 설정을 찾을 수 없습니다."),
    SCHEDULE_EXCEPTION_CONFLICT(HttpStatus.CONFLICT, "SCHEDULE_EXCEPTION_CONFLICT", "해당 날짜 설정이 이미 존재하거나 변경되었습니다. 새로 조회해주세요."),

    INVALID_APPROVER(HttpStatus.BAD_REQUEST, "INVALID_APPROVER", "결재자로 지정할 수 없는 사용자입니다."),
    INVALID_CC_USER(HttpStatus.BAD_REQUEST, "INVALID_CC_USER", "참조자로 지정할 수 없는 사용자입니다."),

    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "INVALID_LOGIN", "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.UNAUTHORIZED, "INVALID_CURRENT_PASSWORD", "현재 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다."),
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "활성 상태의 계정만 사용할 수 있습니다."),
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_VIOLATION", "새 비밀번호는 8자 이상 72자 이하로 입력해야 합니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "SAME_AS_CURRENT_PASSWORD", "현재 비밀번호와 다른 새 비밀번호를 입력해야 합니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    EMPLOYEE_NUMBER_EXHAUSTED(HttpStatus.CONFLICT, "EMPLOYEE_NUMBER_EXHAUSTED", "해당 직렬과 입사 연도의 사번 발급 한도를 초과했습니다."),
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND", "부서를 찾을 수 없습니다."),
    DEPARTMENT_IN_USE(HttpStatus.CONFLICT, "DEPARTMENT_IN_USE", "하위 부서 또는 소속 사용자가 있어 삭제할 수 없습니다."),
    POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "POSITION_NOT_FOUND", "직급을 찾을 수 없습니다."),
    APPROVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND", "결재 문서를 찾을 수 없습니다."),
    APPROVAL_NOT_EDITABLE(HttpStatus.CONFLICT, "APPROVAL_NOT_EDITABLE", "임시저장 상태의 문서만 수정하거나 삭제할 수 있습니다."),
    APPROVAL_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "APPROVAL_NOT_IN_PROGRESS", "진행 중인 결재 문서만 처리할 수 있습니다."),
    APPROVAL_DECISION_FORBIDDEN(HttpStatus.FORBIDDEN, "APPROVAL_DECISION_FORBIDDEN", "현재 결재자만 문서를 처리할 수 있습니다."),
    APPROVAL_LINE_NOT_PROCESSABLE(HttpStatus.CONFLICT, "APPROVAL_LINE_NOT_PROCESSABLE", "현재 결재선을 처리할 수 없는 상태입니다."),
    REJECTION_COMMENT_REQUIRED(HttpStatus.BAD_REQUEST, "REJECTION_COMMENT_REQUIRED", "반려 사유를 입력해야 합니다."),
    APPROVAL_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN, "APPROVAL_CANCEL_FORBIDDEN", "작성자만 상신을 취소할 수 있습니다."),
    APPROVAL_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "APPROVAL_CANCEL_NOT_ALLOWED", "이미 처리된 결재자가 있어 상신을 취소할 수 없습니다."),
    APPROVAL_TERMINATE_FORBIDDEN(HttpStatus.FORBIDDEN, "APPROVAL_TERMINATE_FORBIDDEN", "시스템 관리자만 결재를 강제 종료할 수 있습니다."),
    APPROVAL_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "APPROVAL_MANAGEMENT_FORBIDDEN", "시스템 관리자만 전체 결재를 관리할 수 있습니다."),
    APPROVAL_TERMINATE_NOT_ALLOWED(HttpStatus.CONFLICT, "APPROVAL_TERMINATE_NOT_ALLOWED", "현재 결재자가 활성 상태여서 강제 종료할 수 없습니다."),
    APPROVAL_TERMINATE_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "APPROVAL_TERMINATE_REASON_REQUIRED", "강제 종료 사유를 입력해야 합니다."),
    DELEGATION_NOT_FOUND(HttpStatus.NOT_FOUND, "DELEGATION_NOT_FOUND", "결재 위임을 찾을 수 없습니다."),
    DELEGATION_FORBIDDEN(HttpStatus.FORBIDDEN, "DELEGATION_FORBIDDEN", "해당 결재 위임을 관리할 권한이 없습니다."),
    DELEGATION_INVALID(HttpStatus.BAD_REQUEST, "DELEGATION_INVALID", "위임 대상 또는 기간을 확인해주세요."),
    DELEGATION_OVERLAP(HttpStatus.CONFLICT, "DELEGATION_OVERLAP", "이미 겹치는 기간의 결재 위임이 있습니다."),
    DELEGATION_CANCELED(HttpStatus.CONFLICT, "DELEGATION_CANCELED", "이미 취소된 결재 위임입니다."),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "첨부파일을 찾을 수 없습니다."),
    ATTACHMENT_LIMIT_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_LIMIT_EXCEEDED", "첨부파일의 개수 또는 용량 제한을 초과했습니다."),
    ATTACHMENT_TYPE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "ATTACHMENT_TYPE_NOT_ALLOWED", "허용되지 않는 첨부파일 형식입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다."),
    ATTACHMENT_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ATTACHMENT_STORAGE_FAILED", "첨부파일을 저장하거나 읽는 중 오류가 발생했습니다."),
    WORK_POLICY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "WORK_POLICY_NOT_CONFIGURED", "적용할 수 있는 근무정책이 없습니다."),
    WORK_POLICY_CONFIGURATION_CONFLICT(HttpStatus.INTERNAL_SERVER_ERROR, "WORK_POLICY_CONFIGURATION_CONFLICT", "적용 가능한 근무정책이 여러 개입니다."),
    WORK_POLICY_DAY_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "WORK_POLICY_DAY_NOT_CONFIGURED", "근무정책의 요일 설정이 없습니다."),
    ATTENDANCE_NOT_WORKING_DAY(HttpStatus.CONFLICT, "ATTENDANCE_NOT_WORKING_DAY", "근무일에만 출근할 수 있습니다."),
    ATTENDANCE_VIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "ATTENDANCE_VIEW_FORBIDDEN", "해당 직원 근태를 조회할 권한이 없습니다."),
    ATTENDANCE_CHECK_IN_CLOSED(HttpStatus.CONFLICT, "ATTENDANCE_CHECK_IN_CLOSED", "출근 가능 시간이 종료되었습니다."),
    ATTENDANCE_ALREADY_CHECKED_IN(HttpStatus.CONFLICT, "ATTENDANCE_ALREADY_CHECKED_IN", "이미 출근 처리되었습니다."),
    ATTENDANCE_NOT_CHECKED_IN(HttpStatus.CONFLICT, "ATTENDANCE_NOT_CHECKED_IN", "출근 기록이 없어 퇴근할 수 없습니다."),
    ATTENDANCE_ALREADY_CHECKED_OUT(HttpStatus.CONFLICT, "ATTENDANCE_ALREADY_CHECKED_OUT", "이미 퇴근 처리되었습니다."),
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
