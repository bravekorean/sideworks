package com.example.sideworks.approval.service;

import com.example.sideworks.approval.entity.ApprovalTemplate;
import com.example.sideworks.approval.repository.ApprovalTemplateRepository;
import com.example.sideworks.notification.service.NotificationService;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApprovalTemplateBlockServiceTest {
    @Test
    void 이미_차단한_템플릿은_알림을_다시_기록하지_않는다() {
        ApprovalTemplateRepository templates = mock(ApprovalTemplateRepository.class);
        UserRepository users = mock(UserRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        ApprovalTemplate template = mock(ApprovalTemplate.class);
        when(templates.findForBlock(7L)).thenReturn(Optional.of(template));
        when(template.getVersion()).thenReturn(2L);
        when(template.block()).thenReturn(false);

        Long version = new ApprovalTemplateBlockService(templates, users, notifications).block(7L, 2L);

        assertThat(version).isEqualTo(2L);
        verifyNoInteractions(users, notifications);
        verify(templates, never()).flush();
    }

    @Test
    void 새_차단은_시스템_관리자에게_버전별_알림을_남긴다() {
        ApprovalTemplateRepository templates = mock(ApprovalTemplateRepository.class);
        UserRepository users = mock(UserRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        ApprovalTemplate template = mock(ApprovalTemplate.class);
        User admin = mock(User.class);
        when(templates.findForBlock(7L)).thenReturn(Optional.of(template));
        when(template.getVersion()).thenReturn(2L, 3L);
        when(template.block()).thenReturn(true);
        when(template.getTemplateName()).thenReturn("휴가 결재선");
        when(users.findAllByStatusAndUserRoleInOrderByUserNameAsc(UserStatus.ACTIVE,
                List.of(UserRole.SUPER_ADMIN))).thenReturn(List.of(admin));
        when(admin.getUserId()).thenReturn(1L);

        Long version = new ApprovalTemplateBlockService(templates, users, notifications).block(7L, 2L);

        assertThat(version).isEqualTo(3L);
        verify(notifications).recordTemplate(eq(7L), eq(admin), eq("BLOCKED:2"), anyString(), anyString());
        verify(templates).flush();
    }
}
