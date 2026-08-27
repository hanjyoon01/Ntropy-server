package com.ntropy.account.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.service.VirtualFinancialDataService.GenerationSummary;

import lombok.RequiredArgsConstructor;

/**
 * VIRTUAL 재등록 시 로그인 사용자의 {@code provider=NTROPY} 계좌·거래만 삭제한 뒤 다시 생성한다.
 * 실제 {@code provider=CODEF} 데이터와 다른 사용자의 NTROPY 데이터, 사용자의 NTROPY
 * {@code CODEF_CONNECTION} 행 자체는 건드리지 않는다. 삭제와 재생성은 하나의 트랜잭션으로 묶여
 * 재생성이 실패하면 삭제도 함께 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class VirtualAccountRegenerationService {

    private final AccountMapper accountMapper;
    private final AccountTransactionMapper accountTransactionMapper;
    private final VirtualFinancialDataService virtualFinancialDataService;

    @Transactional
    public GenerationSummary regenerateForUser(Long userId, PersonalBank bank) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 양수여야 합니다");
        }
        if (bank == null) {
            throw new IllegalArgumentException("은행이 필요합니다");
        }

        String provider = ConnectionProvider.NTROPY.name();
        accountTransactionMapper.deleteByUserIdAndProvider(userId, provider);
        accountMapper.deleteByUserIdAndProvider(userId, provider);

        return virtualFinancialDataService.generateForUser(userId, bank);
    }
}
