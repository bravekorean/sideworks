package com.example.sideworks.notification.service;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.notification.entity.Notification;
import com.example.sideworks.notification.entity.NotificationType;
import com.example.sideworks.notification.dto.NotificationResponse;
import com.example.sideworks.notification.event.NotificationCreated;
import com.example.sideworks.notification.repository.NotificationRepository;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository repository;
    @Mock UserRepository users;
    @Mock org.springframework.context.ApplicationEventPublisher publisher;
    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, users, publisher);
    }

    @Test
    void 템플릿_알림은_문서_ID_없이_관리_화면_대상을_반환한다() {
        Notification notification = Notification.createTemplate(mock(User.class), 7L,
                "BLOCKED:2", "결재선 수정 필요", "구성원을 확인해주세요.");

        NotificationResponse response = NotificationResponse.from(notification);

        assertThat(response.approvalId()).isNull();
        assertThat(response.approvalTemplateId()).isEqualTo(7L);
        assertThat(response.type()).isEqualTo(NotificationType.APPROVAL_TEMPLATE_BLOCKED);
    }

    @Test
    void 본인_알림만_읽음_처리한다() {
        User user = mock(User.class);
        Notification notification = mock(Notification.class);
        when(user.getUserId()).thenReturn(5L);
        when(users.findByLoginId("owner")).thenReturn(Optional.of(user));
        when(repository.findByNotificationIdAndRecipient_UserId(9L, 5L)).thenReturn(Optional.of(notification));

        service.markRead("owner", 9L);

        verify(notification).markRead(any());
    }

    @Test
    void 다른_사용자의_알림은_존재를_노출하지_않는다() {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(5L);
        when(users.findByLoginId("owner")).thenReturn(Optional.of(user));
        when(repository.findByNotificationIdAndRecipient_UserId(9L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead("owner", 9L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void 안읽은_개수도_로그인_사용자로_범위를_제한한다() {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(5L);
        when(users.findByLoginId("owner")).thenReturn(Optional.of(user));
        when(repository.countByRecipient_UserIdAndReadAtIsNull(5L)).thenReturn(3L);

        assertThat(service.unreadCount("owner")).isEqualTo(3L);
    }

    @Test
    void 결재_알림을_저장한_뒤_수신자_이벤트를_발행한다() {
        User recipient = mock(User.class);
        Approval approval = mock(Approval.class);
        Notification saved = mock(Notification.class);
        when(recipient.getUserId()).thenReturn(5L);
        when(approval.getApprovalId()).thenReturn(10L);
        when(repository.save(any(Notification.class))).thenReturn(saved);
        when(saved.getNotificationId()).thenReturn(11L);
        when(saved.getApproval()).thenReturn(approval);
        when(saved.getNotificationType()).thenReturn(NotificationType.APPROVAL_REQUEST);
        when(saved.getTitle()).thenReturn("결재 요청");
        when(saved.getMessage()).thenReturn("휴가 신청");

        service.record(approval, recipient, NotificationType.APPROVAL_REQUEST,
                "SUBMITTED", "결재 요청", "휴가 신청");

        var event = org.mockito.ArgumentCaptor.forClass(NotificationCreated.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().recipientId()).isEqualTo(5L);
        assertThat(event.getValue().notification().approvalId()).isEqualTo(10L);
    }
}
