package com.example.sideworks.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "employee_number_sequencetbl",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_employee_number_sequence_family_year",
                columnNames = {"job_family", "hire_year"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeNumberSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_number_sequence_id")
    private Long employeeNumberSequenceId;

    @Column(name = "job_family", nullable = false, length = 20)
    private String jobFamily;

    @Column(name = "hire_year", nullable = false)
    private Integer hireYear;

    @Column(name = "last_sequence", nullable = false)
    private Integer lastSequence;
}
