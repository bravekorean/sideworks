package com.example.sideworks.config;

import com.example.sideworks.auth.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // JWT 기반 REST API는 서버 세션을 사용하지 않으므로 CSRF 보호 대상인 세션 기반 인증과 분리한다.
                .csrf(AbstractHttpConfigurer::disable)
                // 프론트엔드 개발 서버와 API 서버의 출처가 다를 수 있으므로 CORS 정책을 명시한다.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // access token을 매 요청마다 검증하는 구조이므로 서버에 로그인 세션을 저장하지 않는다.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // JWT 로그인에서는 Spring Security 기본 로그인 폼과 Basic 인증을 사용하지 않는다.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 로그인, 토큰 재발급 등 인증 진입점은 토큰 없이 접근 가능해야 한다.
                        .requestMatchers("/api/auth/**").permitAll()
                        // 그 외 API는 JwtAuthenticationFilter에서 인증된 요청만 접근하도록 제한한다.
                        .anyRequest().authenticated()
                )
                // UsernamePasswordAuthenticationFilter보다 먼저 JWT를 검사해 SecurityContext에 인증 정보를 등록한다.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 비밀번호는 복호화가 아니라 단방향 해시로 검증해야 하므로 BCrypt를 사용한다.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 허용 출처는 application-local.properties 등 환경별 설정에서 관리한다.
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // refresh token을 HttpOnly Cookie로 주고받기 위해 credentials 허용이 필요하다.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 현재는 모든 API 경로에 동일한 CORS 정책을 적용한다.
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
