package com.example.sideworks.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Getter
// 생성일과 수정일이 모두 필요한 Entity가 상속하는 공통 클래스이다.
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedEntity {

    @LastModifiedDate
    // Entity가 수정될 때 JPA Auditing이 마지막 수정 시간을 자동으로 갱신한다.
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
