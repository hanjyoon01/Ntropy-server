DROP TABLE IF EXISTS USERS;

CREATE TABLE USERS (
                       user_id BIGINT AUTO_INCREMENT PRIMARY KEY,          -- 회원 고유 번호
                       email VARCHAR(100),                                 -- 이메일
                       name VARCHAR(50),                                   -- 이름 (또는 닉네임)
                       provider_id VARCHAR(100),                           -- 소셜 로그인 제공자 고유 ID
                       refresh_token_hash VARCHAR(255),                    -- 리프레시 토큰 암호화 값
                       alarm_agree BOOLEAN DEFAULT FALSE,                  -- 알림 수신 동의 여부
                       location_agree BOOLEAN DEFAULT FALSE,               -- 위치정보 수집 동의 여부
                       refresh_token_expire_at DATETIME,                   -- 리프레시 토큰 만료 일시
                       provider VARCHAR(20),                               -- 소셜 제공자 (KAKAO, GOOGLE 등)
                       status VARCHAR(20) DEFAULT 'ACTIVE',                -- 계정 상태 (ACTIVE, INACTIVE 등)
                       role VARCHAR(20) DEFAULT 'ROLE_USER',               -- 권한 (ROLE_USER, ROLE_ADMIN)
                       created_at DATETIME DEFAULT CURRENT_TIMESTAMP,      -- 가입 일시
                       last_login_at DATETIME,                             -- 마지막 로그인 일시
                       onboarding_completed BOOLEAN DEFAULT FALSE,         -- 온보딩(초기 설정) 완료 여부
                       terms_agreed BOOLEAN DEFAULT FALSE,                 -- 약관 동의 여부
                       UNIQUE KEY uk_users_provider_provider_id (provider, provider_id)
);