/**
 * ai-service가 소유한, account-service에 대한 아웃바운드 포트 계층. ai의 도메인 코어는
 * account-service의 계약(common.client.MonthlyExpenseQueryClient,
 * AccountTransactionAnalysisClient)을 직접 참조하지 않고 이 포트만 알면 된다. 실제 호출은
 * adapter.account가 담당한다.
 */
package com.ntropy.ai.port.account;
