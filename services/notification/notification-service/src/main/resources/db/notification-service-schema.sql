DROP TABLE IF EXISTS NOTIFICATION;

CREATE TABLE NOTIFICATION (
                              notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,   -- 알림 고유 번호
                              user_id BIGINT NOT NULL,                             -- 수신 회원 번호
                              event_id VARCHAR(100) NOT NULL,                      -- 발생 이벤트 식별자 (중복 생성 방지용 멱등성 키)
                              notification_type VARCHAR(30) NOT NULL,              -- 알림 유형 (예: DEFENSE_MODE, PAYMENT, WORK 등)
                              title VARCHAR(500) NOT NULL,                         -- 알림 제목
                              body VARCHAR(500) NOT NULL,                          -- 알림 본문
                              read_at DATETIME NULL,                               -- 읽음 처리 일시
                              created_at DATETIME DEFAULT CURRENT_TIMESTAMP,       -- 생성 일시
                              deleted_at DATETIME NULL,                            -- 삭제(soft delete) 일시
                              CONSTRAINT uq_notification_event UNIQUE (event_id),
                              INDEX idx_notification_user (user_id, created_at)
);

DROP TABLE IF EXISTS PUSH_SUBSCRIPTION;

CREATE TABLE PUSH_SUBSCRIPTION (
                                   subscription_id BIGINT AUTO_INCREMENT PRIMARY KEY,   -- 구독 고유 번호
                                   user_id BIGINT NOT NULL,                             -- 구독한 회원 번호
                                   endpoint VARCHAR(500) NOT NULL,                      -- 브라우저 푸시 서비스 endpoint URL (구독 단위 식별자)
                                   p256dh VARCHAR(200) NOT NULL,                        -- 암호화 공개키
                                   auth VARCHAR(100) NOT NULL,                          -- 인증 시크릿
                                   created_at DATETIME DEFAULT CURRENT_TIMESTAMP,       -- 구독 등록 일시
                                   CONSTRAINT uq_push_subscription_endpoint UNIQUE (endpoint),
                                   INDEX idx_push_subscription_user (user_id)
);

