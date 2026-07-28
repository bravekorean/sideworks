package com.example.sideworks.auth.jwt;

import com.example.sideworks.user.entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            "test-secret-key-test-secret-key-test-secret-key-1234567890",
            1_800_000,
            604_800_000
    );

    @Test
    void accessToken을_생성하고_검증할_수_있다() {
        // given
        Long userId = 1L;
        String loginId = "admin";
        UserRole userRole = UserRole.SUPER_ADMIN;

        // when
        String token = jwtTokenProvider.createAccessToken(userId, loginId, userRole);

        // then
        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void accessToken에서_사용자_정보를_꺼낼_수_있다() {
        // given
        Long userId = 1L;
        String loginId = "admin";
        UserRole userRole = UserRole.SUPER_ADMIN;
        String token = jwtTokenProvider.createAccessToken(userId, loginId, userRole);

        // when
        Long extractedUserId = jwtTokenProvider.getUserId(token);
        String extractedLoginId = jwtTokenProvider.getLoginId(token);
        UserRole extractedUserRole = jwtTokenProvider.getUserRole(token);

        // then
        assertThat(extractedUserId).isEqualTo(userId);
        assertThat(extractedLoginId).isEqualTo(loginId);
        assertThat(extractedUserRole).isEqualTo(userRole);
    }

    @Test
    void refreshToken을_생성하고_검증할_수_있다() {
        // given
        Long userId = 1L;

        // when
        String token = jwtTokenProvider.createRefreshToken(userId);

        // then
        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.validateRefreshToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(userId);
    }

    @Test
    void accessToken은_refreshToken으로_사용할_수_없다() {
        String accessToken = jwtTokenProvider.createAccessToken(1L, "admin", UserRole.SUPER_ADMIN);

        assertThat(jwtTokenProvider.validateRefreshToken(accessToken)).isFalse();
    }
}
