package com.example.sideworks.attendance.entity;

import com.example.sideworks.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "work_schedule_exceptiontbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkScheduleException extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long workScheduleExceptionId;
    @Column(nullable = false, unique = true)
    private LocalDate exceptionDate;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false)
    private boolean working;
    @Column(nullable = false)
    private boolean active;
    @Version
    private long version;

    public static WorkScheduleException create(LocalDate date, String name, boolean working) {
        WorkScheduleException entry = new WorkScheduleException();
        entry.exceptionDate = date;
        entry.update(name, working, true);
        return entry;
    }

    public void update(String name, boolean working, boolean active) {
        this.name = name;
        this.working = working;
        this.active = active;
    }
}
