package com.example.sideworks.attendance.service;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.factory.ApprovalSubmissionFactory;
import com.example.sideworks.approval.repository.*;
import com.example.sideworks.attendance.entity.*;
import com.example.sideworks.attendance.repository.*;
import com.example.sideworks.common.exception.*;
import com.example.sideworks.user.entity.*;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.Optional;
import java.util.List;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceCorrectionServiceTest {
    @Mock UserRepository users;
    @Mock AttendanceRepository attendances;
    @Mock AttendanceCorrectionRepository corrections;
    @Mock AttendanceChangeHistoryRepository histories;
    @Mock WorkPolicyRepository policies;
    @Mock ApprovalRepository approvals;
    @Mock ApprovalLineRepository lines;
    @Mock ApprovalHistoryRepository approvalHistories;
    @Mock ApprovalSubmissionFactory submissionFactory;
    @Mock com.example.sideworks.approval.attachment.service.ApprovalAttachmentService attachments;
    AttendanceCorrectionService service;
    @Mock com.example.sideworks.approval.service.ApprovalDocumentTypeService documentTypes;

    @BeforeEach void setUp() {
        service = new AttendanceCorrectionService(users, attendances, corrections, histories, policies,
                approvals, lines, approvalHistories, submissionFactory,
                Clock.fixed(Instant.parse("2026-09-16T09:00:00Z"), ZoneId.of("Asia/Seoul")), attachments, documentTypes,
                org.mockito.Mockito.mock(com.example.sideworks.notification.service.ApprovalNotificationWorkflow.class));
    }

    @Test void 최종승인시_원본을_수정하고_이력을_저장한다() {
        User employee = user(1L, UserRole.USER);
        User actor = user(2L, UserRole.HR_MANAGER);
        Approval approval = mock(Approval.class);
        when(approval.getApprovalId()).thenReturn(30L);
        Attendance original = attendance(10L, 3L,
                LocalDateTime.parse("2026-09-15T09:30"), LocalDateTime.parse("2026-09-15T18:00"));
        AttendanceCorrection correction = AttendanceCorrection.create(employee, approval,
                LocalDate.of(2026, 9, 15), original,
                LocalDateTime.parse("2026-09-15T09:00"), LocalDateTime.parse("2026-09-15T18:00"), "단말 오류");
        when(corrections.findByApproval_ApprovalId(30L)).thenReturn(Optional.of(correction));
        when(attendances.findByUserIdAndAttendanceDateForUpdate(1L, LocalDate.of(2026, 9, 15)))
                .thenReturn(Optional.of(original));

        service.onDecision(approval, actor, true);

        verify(attendances).saveAndFlush(original);
        verify(histories).save(any(AttendanceChangeHistory.class));
        assertThat(correction.getPendingDate()).isNull();
    }

    @Test void 신청후_원본버전이_변경되면_승인을_거절한다() {
        User employee = user(1L, UserRole.USER);
        User actor = user(2L, UserRole.HR_MANAGER);
        Approval approval = mock(Approval.class);
        when(approval.getApprovalId()).thenReturn(30L);
        Attendance submitted = attendance(10L, 3L,
                LocalDateTime.parse("2026-09-15T09:30"), null);
        Attendance current = attendance(10L, 4L,
                LocalDateTime.parse("2026-09-15T09:30"), LocalDateTime.parse("2026-09-15T18:00"));
        AttendanceCorrection correction = AttendanceCorrection.create(employee, approval,
                LocalDate.of(2026, 9, 15), submitted,
                LocalDateTime.parse("2026-09-15T09:00"), LocalDateTime.parse("2026-09-15T18:00"), "단말 오류");
        when(corrections.findByApproval_ApprovalId(30L)).thenReturn(Optional.of(correction));
        when(attendances.findByUserIdAndAttendanceDateForUpdate(1L, LocalDate.of(2026, 9, 15)))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.onDecision(approval, actor, true))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.ATTENDANCE_CORRECTION_STALE);
        verify(histories, never()).save(any());
        assertThat(correction.getPendingDate()).isNotNull();
    }

    @Test void 일반사용자는_직접정정할수없다() {
        User ordinary = user(1L, UserRole.USER);
        when(users.findByLoginId("user")).thenReturn(Optional.of(ordinary));
        AttendanceCorrectionService.Input input = new AttendanceCorrectionService.Input(
                LocalDate.of(2026, 9, 15), LocalDateTime.parse("2026-09-15T09:00"), null,
                "정정", null, null, null);
        assertThatThrownBy(() -> service.direct("user", 1L, input))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.ATTENDANCE_CHANGE_FORBIDDEN);
    }

    @Test void 첨부를_임시상태에서_저장한뒤_정정문서를_상신한다() {
        Approval draft = prepareDocument();
        MockMultipartFile file = new MockMultipartFile("files", "evidence.pdf", "application/pdf", new byte[]{1});
        when(attachments.upload(30L, "employee", List.of(file))).thenAnswer(invocation -> {
            assertThat(draft.isDraft()).isTrue();
            return List.of();
        });

        Long id = service.submitDocument("employee", document(), List.of(file));

        assertThat(id).isEqualTo(30L);
        assertThat(draft.isInProgress()).isTrue();
        verify(attachments).upload(30L, "employee", List.of(file));
        verify(corrections).saveAndFlush(any(AttendanceCorrection.class));
        verify(lines).saveAll(any());
    }

    @Test void 첨부없는_정정문서도_상신한다() {
        Approval draft = prepareDocument();

        service.submitDocument("employee", document(), List.of());

        assertThat(draft.isInProgress()).isTrue();
        verifyNoInteractions(attachments);
        verify(corrections).saveAndFlush(any(AttendanceCorrection.class));
    }

    @Test void 첨부실패시_결재선과_정정요청을_생성하지_않는다() {
        Approval draft = prepareDocument();
        MockMultipartFile file = new MockMultipartFile("files", "bad.json", "application/json", new byte[]{1});
        when(attachments.upload(30L, "employee", List.of(file)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> service.submitDocument("employee", document(), List.of(file)))
                .isInstanceOf(BusinessException.class);

        assertThat(draft.isDraft()).isTrue();
        verify(lines, never()).saveAll(any());
        verify(corrections, never()).saveAndFlush(any());
    }

    private AttendanceCorrectionService.DocumentInput document() {
        return new AttendanceCorrectionService.DocumentInput("출근 기록 정정", new AttendanceCorrectionService.Input(
                LocalDate.of(2026, 9, 15), LocalDateTime.parse("2026-09-15T09:00"), null,
                "출근 누락", 2L, null, null));
    }

    private Approval prepareDocument() {
        User employee = user(1L, UserRole.USER);
        User hr = user(2L, UserRole.HR_MANAGER);
        when(users.findByLoginId("employee")).thenReturn(Optional.of(employee));
        when(users.findById(2L)).thenReturn(Optional.of(hr));
        var type = com.example.sideworks.approval.entity.ApprovalDocumentTypeFixtures.type(
                6L, "ATTENDANCE_CORRECTION", com.example.sideworks.approval.entity.DocumentBehaviorType.ATTENDANCE_CORRECTION, true);
        when(documentTypes.requireAttendanceCorrection()).thenReturn(type);
        Approval draft = Approval.createDraft(employee, "출근 기록 정정", "내용", type);
        lenient().when(submissionFactory.createLines(any(), any(), any())).thenReturn(List.of(
                com.example.sideworks.approval.entity.ApprovalLine.create(draft, hr, 1,
                        com.example.sideworks.approval.entity.ApprovalLineStatus.PENDING)));
        ReflectionTestUtils.setField(draft, "approvalId", 30L);
        when(approvals.save(any(Approval.class))).thenAnswer(invocation -> {
            Approval submitted = invocation.getArgument(0);
            assertThat(submitted.getTitle()).isEqualTo("출근 기록 정정");
            assertThat(submitted.getDocumentType()).isSameAs(type);
            assertThat(submitted.getLegacyDocumentType()).isEqualTo("ATTENDANCE_CORRECTION");
            assertThat(submitted.getContent()).contains("출근 누락", "2026-09-15");
            return draft;
        });
        return draft;
    }

    private User user(Long id, UserRole role) {
        User u = mock(User.class);
        lenient().when(u.getUserId()).thenReturn(id);
        lenient().when(u.getUserRole()).thenReturn(role);
        lenient().when(u.getStatus()).thenReturn(UserStatus.ACTIVE);
        return u;
    }
    private Attendance attendance(Long id, long version, LocalDateTime in, LocalDateTime out) {
        Attendance a = mock(Attendance.class);
        lenient().when(a.getAttendanceId()).thenReturn(id);
        lenient().when(a.getVersion()).thenReturn(version);
        lenient().when(a.getCheckInAt()).thenReturn(in);
        lenient().when(a.getCheckOutAt()).thenReturn(out);
        return a;
    }
}
