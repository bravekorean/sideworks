package com.example.sideworks.attendance.repository;

import com.example.sideworks.attendance.entity.WorkPolicyDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkPolicyDayRepository extends JpaRepository<WorkPolicyDay, Long> {

    Optional<WorkPolicyDay> findByWorkPolicy_WorkPolicyIdAndDayOfWeek(
            Long workPolicyId,
            DayOfWeek dayOfWeek
    );

    List<WorkPolicyDay> findAllByWorkPolicy_WorkPolicyIdIn(Collection<Long> workPolicyIds);
}
