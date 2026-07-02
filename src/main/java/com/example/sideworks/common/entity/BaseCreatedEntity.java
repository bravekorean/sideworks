package com.example.sideworks.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
// 공통 생성일 컬럼을 Entity에 상속시키기 위한 JPA 부모 클래스이다. 별도 테이블은 생성되지 않는다.
@MappedSuperclass
// @CreatedDate가 동작하도록 AuditingEntityListener를 연결한다.
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseCreatedEntity {

    @CreatedDate
    // 생성일은 최초 저장 시점에만 기록하고 이후 수정 쿼리에서는 변경하지 않는다.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
