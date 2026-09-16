package com.example.sideworks.attendance.service;

import com.example.sideworks.attendance.entity.WorkScheduleException;
import com.example.sideworks.attendance.repository.WorkScheduleExceptionRepository;
import com.example.sideworks.common.exception.*;
import com.example.sideworks.user.entity.*;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.time.LocalDate;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class WorkScheduleExceptionService {
    private final WorkScheduleExceptionRepository repository;
    private final UserRepository users;

    public record Input(LocalDate date, String name, Boolean working, Boolean active, Long version) {}
    public record Entry(Long id, LocalDate date, String name, boolean working, boolean active, long version) {
        static Entry from(WorkScheduleException e) {
            return new Entry(e.getWorkScheduleExceptionId(), e.getExceptionDate(), e.getName(),
                    e.isWorking(), e.isActive(), e.getVersion());
        }
    }

    public List<Entry> list(String login, int year) {
        manager(login);
        if (year < 1900 || year > 2100) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return repository.findAllByExceptionDateBetweenOrderByExceptionDateAsc(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream().map(Entry::from).toList();
    }

    @Transactional
    public Entry create(String login, Input input) {
        User actor = manager(login);
        validate(input);
        WorkScheduleException entry = WorkScheduleException.create(input.date(), input.name().trim(), input.working());
        try { repository.saveAndFlush(entry); }
        catch (DataIntegrityViolationException e) { throw new BusinessException(ErrorCode.SCHEDULE_EXCEPTION_CONFLICT); }
        AttendanceAudit.afterCommit("WORK_SCHEDULE_CREATED", actor.getUserId(), entry.getWorkScheduleExceptionId());
        return Entry.from(entry);
    }

    @Transactional
    public Entry update(String login, Long id, Input input) {
        User actor = manager(login);
        validate(input);
        WorkScheduleException entry = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_EXCEPTION_NOT_FOUND));
        if (input.version() == null || entry.getVersion() != input.version())
            throw new BusinessException(ErrorCode.SCHEDULE_EXCEPTION_CONFLICT);
        if (!entry.getExceptionDate().equals(input.date()) || input.active() == null)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        entry.update(input.name().trim(), input.working(), input.active());
        try { repository.flush(); }
        catch (ObjectOptimisticLockingFailureException e) { throw new BusinessException(ErrorCode.SCHEDULE_EXCEPTION_CONFLICT); }
        AttendanceAudit.afterCommit("WORK_SCHEDULE_UPDATED", actor.getUserId(), id);
        return Entry.from(entry);
    }

    private User manager(String login) {
        User user = users.findByLoginId(login).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE || (user.getUserRole() != UserRole.HR_MANAGER
                && user.getUserRole() != UserRole.SUPER_ADMIN))
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_FORBIDDEN);
        return user;
    }

    private void validate(Input input) {
        if (input == null || input.date() == null || input.date().getYear() < 1900 || input.date().getYear() > 2100
                || input.name() == null || input.name().isBlank() || input.name().trim().length() > 100
                || input.working() == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
}
