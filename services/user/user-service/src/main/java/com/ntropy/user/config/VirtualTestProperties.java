package com.ntropy.user.config;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

import com.ntropy.user.virtual.VirtualUserProviderId;

/**
 * 가상회원 시딩·테스트 로그인 설정. {@code virtual-test.enabled}만으로는 운영 노출을 막기에 부족해,
 * 활성화 시 {@code deployment.environment}가 local/test가 아니면 빈 생성 자체를 실패시켜 애플리케이션 기동을 막는다.
 */
@Getter
@Component
public class VirtualTestProperties {

    private static final Set<String> ALLOWED_ENVIRONMENTS_WHEN_ENABLED = Set.of("local", "test");

    private final boolean enabled;
    private final int userCount;
    private final long randomSeed;
    private final LocalDate referenceDate;
    private final String datasetVersion;
    private final String deploymentEnvironment;

    public VirtualTestProperties(
            @Value("${virtual-test.enabled:false}") boolean enabled,
            @Value("${virtual-test.user-count:50}") int userCount,
            @Value("${virtual-test.random-seed:20260817}") long randomSeed,
            @Value("${virtual-test.reference-date:2026-08-17}") String referenceDate,
            @Value("${virtual-test.dataset-version:VIRTUAL-MULTI-DOMAIN-v1}") String datasetVersion,
            @Value("${deployment.environment:unknown}") String deploymentEnvironment
    ) {
        String normalizedEnvironment = deploymentEnvironment == null
                ? "unknown"
                : deploymentEnvironment.trim().toLowerCase(Locale.ROOT);
        if (enabled && !ALLOWED_ENVIRONMENTS_WHEN_ENABLED.contains(normalizedEnvironment)) {
            throw new IllegalStateException(
                    "virtual-test.enabled=true는 deployment.environment가 local 또는 test일 때만 허용됩니다. "
                            + "현재 deployment.environment=" + normalizedEnvironment
            );
        }
        if (userCount < 1 || userCount > VirtualUserProviderId.MAX_ORDINAL) {
            throw new IllegalArgumentException(
                    "virtual-test.user-count는 1.." + VirtualUserProviderId.MAX_ORDINAL
                            + " 범위여야 합니다: " + userCount
            );
        }
        if (datasetVersion == null || datasetVersion.isBlank()) {
            throw new IllegalArgumentException("virtual-test.dataset-version은 비어 있을 수 없습니다.");
        }
        this.enabled = enabled;
        this.userCount = userCount;
        this.randomSeed = randomSeed;
        this.referenceDate = LocalDate.parse(referenceDate);
        this.datasetVersion = datasetVersion;
        this.deploymentEnvironment = normalizedEnvironment;
    }

    public boolean isVirtualUserNumberInRange(int virtualUserNumber) {
        return virtualUserNumber >= 1 && virtualUserNumber <= userCount;
    }
}
