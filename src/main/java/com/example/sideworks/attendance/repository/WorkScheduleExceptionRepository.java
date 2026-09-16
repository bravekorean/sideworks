package com.example.sideworks.attendance.repository;

import com.example.sideworks.attendance.entity.WorkScheduleException;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface WorkScheduleExceptionRepository extends JpaRepository<WorkScheduleException, Long> {
    Optional<WorkScheduleException> findByExceptionDateAndActiveTrue(LocalDate date);
    List<WorkScheduleException> findAllByExceptionDateBetweenOrderByExceptionDateAsc(LocalDate from, LocalDate to);
}
