package com.example.sideworks.attendance.repository;
import com.example.sideworks.attendance.entity.AttendanceCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {
    Optional<AttendanceCorrection> findByApproval_ApprovalId(Long approvalId);
    boolean existsByUser_UserIdAndPendingDate(Long userId, LocalDate date);
}
