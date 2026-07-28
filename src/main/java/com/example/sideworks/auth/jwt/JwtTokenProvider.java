package com.example.sideworks.auth.jwt;

import com.example.sideworks.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_LOGIN_ID = "loginId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final String secret;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    // JWT 설정값은 환경별 properties에서 주입받아 로컬/운영 설정을 분리한다.
    public JwtTokenProvider(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.access-token-validity-ms:1800000}") long accessTokenValidityMs,
            @Value("${jwt.refresh-token-validity-ms:604800000}") long refreshTokenValidityMs
    ) {
        this.secret = secret;
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    public String createAccessToken(Long userId, String loginId, UserRole userRole) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenValidityMs);

        // access token은 API 요청 인증에 사용하므로 사용자 식별값과 권한 정보를 함께 담는다.
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_LOGIN_ID, loginId)
                .claim(CLAIM_ROLE, userRole.name())
                .claim(CLAIM_TOKEN_TYPE, ACCESS_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String createRefreshToken(Long userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshTokenValidityMs);

        // refresh token은 access token 재발급용이므로 최소한의 식별 정보만 담는다.
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TOKEN_TYPE, REFRESH_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            // 서명 검증과 만료 시간 검증을 통과하면 유효한 토큰으로 판단한다.
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return claims.get(CLAIM_USER_ID, Long.class);
    }

    public boolean validateRefreshToken(String token) {
        if (!validateToken(token)) {
            return false;
        }

        return REFRESH_TOKEN_TYPE.equals(parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
    }

    public String getLoginId(String token) {
        Claims claims = parseClaims(token);
        return claims.get(CLAIM_LOGIN_ID, String.class);
    }

    public UserRole getUserRole(String token) {
        Claims claims = parseClaims(token);
        return UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

    private Claims parseClaims(String token) {
        // secret key로 서명을 검증한 뒤 payload(claims)를 읽는다.
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        if (secret == null || secret.isBlank()) {
            // secret이 비어 있으면 토큰을 안전하게 만들 수 없으므로 즉시 실패시킨다.
            throw new IllegalStateException("JWT secret is not configured.");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
