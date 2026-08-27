package com.ntropy.account.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * 기업·국민은행 birthDate 저장 암호화(AES-GCM) 키 설정을 로드한다.
 * {@link CodefConfig#propertySourcesPlaceholderConfigurer()}가 이미 전역으로 등록돼 있으므로
 * 여기서는 property source만 추가한다.
 */
@Configuration
@PropertySource(value = "classpath:birth-date-local.properties", ignoreResourceNotFound = true)
public class BirthDateEncryptionConfig {
}
