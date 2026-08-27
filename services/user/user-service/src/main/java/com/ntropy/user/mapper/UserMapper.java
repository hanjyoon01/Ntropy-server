package com.ntropy.user.mapper;

import com.ntropy.user.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserMapper {

    // OAuth 소셜 로그인
    Optional<User> findByProviderAndProviderId(@Param("provider") String provider, @Param("providerId") String providerId);

    void insertUser(User user);

    void updateLoginInfo(User user);

    // 일반 회원 프로필 CRUD
    User findById(Long userId);

    void updateUser(User user);

    void updateOnboardingCompleted(Long userId);

    void deleteUser(Long userId);

    void invalidateRefreshToken(Long userId);

    Optional<User> findByRefreshToken(String refreshToken);

    List<Long> findActiveUserIds(@Param("scope") String scope, @Param("virtualProvider") String virtualProvider);

    List<User> findAllByProvider(@Param("provider") String provider);
}