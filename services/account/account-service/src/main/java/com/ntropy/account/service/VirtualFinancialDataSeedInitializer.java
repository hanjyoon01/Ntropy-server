package com.ntropy.account.service;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * local/test 환경에서 가상 금융 데이터셋 기능을 활성화하면 애플리케이션 기동 중 FIN-005
 * 가상 계좌·거래를 멱등 생성한다({@link VirtualFinancialDataService#generate()}).
 * user-service의 {@code VirtualUserSeedInitializer}가 먼저 USERS에 가상회원을 시딩해야
 * {@link VirtualFinancialDataService#generate()}가 조회할 대상이 생긴다. 모든 일반 singleton의
 * 초기화 콜백이 끝난 뒤 호출되는 {@link SmartInitializingSingleton}을 사용해 회원 시딩 이후에
 * 실행하되, account-service 단독 컨텍스트에서는 user-service 빈을 강제하지 않는다.
 */
@Slf4j
@Component
public class VirtualFinancialDataSeedInitializer implements SmartInitializingSingleton {

    private final VirtualFinancialDataService virtualFinancialDataService;
    private final boolean virtualTestEnabled;

    public VirtualFinancialDataSeedInitializer(
            VirtualFinancialDataService virtualFinancialDataService,
            @Value("${virtual-test.enabled:false}") boolean virtualTestEnabled
    ) {
        this.virtualFinancialDataService = virtualFinancialDataService;
        this.virtualTestEnabled = virtualTestEnabled;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!virtualTestEnabled) {
            return;
        }
        VirtualFinancialDataService.GenerationSummary summary = virtualFinancialDataService.generate();
        log.info(
                "가상 금융 데이터셋 시딩 완료: users={}, accounts={}, transactions={}",
                summary.users(), summary.accounts(), summary.transactions()
        );
    }
}
