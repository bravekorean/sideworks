package com.example.sideworks.user.repository;

import com.example.sideworks.user.entity.EmployeeNumberSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeNumberSequenceRepository extends JpaRepository<EmployeeNumberSequence, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO employee_number_sequencetbl (job_family, hire_year, last_sequence)
            VALUES (:jobFamily, :hireYear, 1)
            ON DUPLICATE KEY UPDATE last_sequence = last_sequence + 1
            """, nativeQuery = true)
    void incrementSequence(
            @Param("jobFamily") String jobFamily,
            @Param("hireYear") int hireYear
    );

    @Query(value = """
            SELECT last_sequence
            FROM employee_number_sequencetbl
            WHERE job_family = :jobFamily
              AND hire_year = :hireYear
            """, nativeQuery = true)
    Integer findLastSequence(
            @Param("jobFamily") String jobFamily,
            @Param("hireYear") int hireYear
    );
}
