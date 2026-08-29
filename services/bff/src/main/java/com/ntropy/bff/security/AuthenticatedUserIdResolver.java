package com.ntropy.bff.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.common.exception.ServiceException;

/** JWT 필터가 구성한 Authentication에서 금융 API용 사용자 ID를 추출한다. */
@Component
public class AuthenticatedUserIdResolver {

    public Long resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Number number) {
            return requirePositive(number.longValue());
        }
        if (principal instanceof UserDetails userDetails) {
            return parse(userDetails.getUsername());
        }
        if (principal instanceof String principalText && !"anonymousUser".equals(principalText)) {
            return parse(principalText);
        }
        return parse(authentication.getName());
    }

    private static Long parse(String value) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return requirePositive(Long.parseLong(value));
        } catch (NumberFormatException e) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static Long requirePositive(long userId) {
        if (userId <= 0) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
