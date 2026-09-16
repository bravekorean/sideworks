package com.example.sideworks.attendance.repository;
import com.example.sideworks.attendance.entity.AttendanceChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceChangeHistoryRepository extends JpaRepository<AttendanceChangeHistory, Long> {
    List<AttendanceChangeHistory> findAllByUserIdAndAttendanceDateOrderByAttendanceChangeHistoryIdDesc(Long userId, LocalDate date);
}
