package com.example.sideworks.attendance.repository;

import com.example.sideworks.attendance.entity.Attendance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findAllByAttendanceDateAndUser_UserIdIn(LocalDate date, List<Long> userIds);

    boolean existsByUser_UserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);

    Optional<Attendance> findByUser_UserIdAndAttendanceDate(
            Long userId,
            LocalDate attendanceDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attendance
            from Attendance attendance
            where attendance.user.userId = :userId
              and attendance.attendanceDate = :attendanceDate
            """)
    Optional<Attendance> findByUserIdAndAttendanceDateForUpdate(
            @Param("userId") Long userId,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    List<Attendance> findAllByUser_UserIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            Long userId,
            LocalDate from,
            LocalDate to
    );
}
