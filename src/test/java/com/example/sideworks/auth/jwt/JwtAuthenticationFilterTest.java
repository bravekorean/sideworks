package com.example.sideworks.auth.jwt;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    @Test
    void 폐기된_역할의_토큰은_서버_오류_대신_401을_반환한다() throws Exception {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer legacy-token");
        when(provider.validateToken("legacy-token")).thenReturn(true);
        when(provider.getUserId("legacy-token")).thenReturn(1L);
        when(provider.getLoginId("legacy-token")).thenReturn("employee");
        when(provider.getUserRole("legacy-token"))
                .thenThrow(new IllegalArgumentException("Unknown role"));

        try {
            new JwtAuthenticationFilter(provider).doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verifyNoInteractions(chain);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
