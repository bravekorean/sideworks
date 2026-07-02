package com.example.sideworks.auth.jwt;

import com.example.sideworks.user.entity.UserRole;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain filterChain) throws ServletException, IOException {

        // Authorization 헤더에서 Bearer 접두어를 제거하고 실제 access token만 추출한다.
        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            String loginId = jwtTokenProvider.getLoginId(token);
            UserRole userRole = jwtTokenProvider.getUserRole(token);

            // Spring Security 권한 규칙(hasRole 등)과 맞추기 위해 ROLE_ 접두어를 붙인다.
            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken (userRole, loginId, userId);

            // 현재 요청을 인증된 사용자 요청으로 인식하도록 SecurityContext에 등록한다.
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 다음 필터 또는 Controller로 요청을 넘기기 위해 반드시 호출해야 한다.
        filterChain.doFilter(request, response);
    }

    private static UsernamePasswordAuthenticationToken UsernamePasswordAuthenticationToken (UserRole userRole, String loginId, Long userId) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + userRole.name());

        // 비밀번호 인증은 로그인 시점에 이미 끝났으므로 credentials는 null로 둔다.
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(loginId, null, List.of(authority));

        // 필요 시 Controller/Service에서 사용자 PK를 참조할 수 있도록 details에 저장한다.
        authentication.setDetails(userId);
        return authentication;
    }

    private String resolveToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        // "Bearer " 길이만큼 제거하면 실제 JWT 문자열만 남는다.
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}
