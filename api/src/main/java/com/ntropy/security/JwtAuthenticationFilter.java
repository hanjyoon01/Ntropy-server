package com.ntropy.security;

import com.ntropy.user.api.client.TokenVerifier;
import com.ntropy.user.api.dto.VerifiedToken;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Authorization 헤더의 액세스 토큰을 검증해 SecurityContext를 채운다.
 * 토큰 검증은 common의 TokenVerifier 계약에 위임하므로 회원 도메인 구현에 의존하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenVerifier tokenVerifier;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String bearerToken = parseBearerToken(request);
        VerifiedToken verified = tokenVerifier.verify(bearerToken);
        if (verified != null) {
            List<GrantedAuthority> authorities = verified.role() == null
                    ? Collections.emptyList()
                    : List.of(new SimpleGrantedAuthority(verified.role()));

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(verified.userId(), null, authorities)
            );
            log.debug("인증 정보를 저장했습니다: userId={}, uri={}", verified.userId(), request.getRequestURI());
        } else {
            // 진단용 로그. 실패 원인을 셋으로 구분한다: 헤더 없음 / Bearer 형식 아님 / 토큰 자체가 무효
            String rawHeader = request.getHeader("Authorization");
            if (rawHeader == null) {
                log.warn("Authorization 헤더가 없습니다: uri={}", request.getRequestURI());
            } else if (bearerToken == null) {
                log.warn("Authorization 헤더가 'Bearer ' 형식이 아닙니다: uri={}, header='{}'",
                        request.getRequestURI(), rawHeader);
            } else {
                log.warn("토큰 검증에 실패했습니다(만료 또는 서명 불일치): uri={}", request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String parseBearerToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
