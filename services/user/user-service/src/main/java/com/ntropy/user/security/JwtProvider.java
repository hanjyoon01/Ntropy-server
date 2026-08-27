package com.ntropy.user.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JwtProvider {

    private final Key key;
    private final long accessTokenValidityInMilliseconds;
    private final long refreshTokenValidityInMilliseconds;

    public JwtProvider(@Value("${jwt.secret}") String secretKey,
                       @Value("${jwt.access-token-expiration-ms}") long accessTokenValidityInMilliseconds,
                       @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenValidityInMilliseconds) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValidityInMilliseconds = accessTokenValidityInMilliseconds;
        this.refreshTokenValidityInMilliseconds = refreshTokenValidityInMilliseconds;
    }

    //Access Token 생성 (role 파라미터 추가)
    public String createAccessToken(String userId, String email, String role) {
        return createToken(userId, email, role, accessTokenValidityInMilliseconds);
    }

    /**
     * Refresh Token 생성
     * @param userId 사용자 ID
     * @return 생성된 Refresh Token 문자열
     */
    public String createRefreshToken(String userId) {
        // Refresh Token은 역할 정보가 필요 없으므로 null 전달
        return createToken(userId, null, null, refreshTokenValidityInMilliseconds);
    }

    /**
     * JWT 토큰 생성
     * @param userId 사용자 ID (Subject)
     * @param email 이메일 (Claim)
     * @param role 사용자 역할 (Claim)
     * @param validityInMilliseconds 만료 시간
     * @return 생성된 JWT 문자열
     */
    private String createToken(String userId, String email, String role, long validityInMilliseconds) {
        Claims claims = Jwts.claims().setSubject(userId);
        if (email != null) {
            claims.put("email", email);
        }
        // <<<--- 역할 정보 추가 --- START --->>>
        if (role != null) {
            claims.put("role", role);
        }
        // <<<--- 역할 정보 추가 --- END --->>>

        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * JWT 토큰에서 사용자 ID(Subject)를 추출함
     * @param token JWT 토큰
     * @return 추출된 사용자 ID
     */
    public String getUserId(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    /**
     * JWT 토큰에서 사용자 역할(Role)을 추출함 (추가)
     * @param token JWT 토큰
     * @return 추출된 사용자 역할
     */
    public String getRole(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return (String) claims.get("role");
    }

    /**
     * JWT 토큰의 유효성을 검증함
     * @param token JWT 토큰
     * @return 유효하면 true, 아니면 false
     */
    public boolean validateToken(String token) {
        try {
            Jws<Claims> claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return !claims.getBody().getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 JWT 토큰: {}", e.getMessage());
            return false;
        }
    }
}
