package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.YearMonth;

import com.ntropy.work.mapper.InMemoryAllocationGoalMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.work.domain.entity.SavingGoal;
import com.ntropy.work.mapper.InMemorySavingGoalMapper;

class SavingGoalServiceTest {

    private static final Long USER_ID = 1L;
    private static final String TARGET_MONTH = "2026-08";
    private static final String CURRENT_MONTH = YearMonth.now().toString();

    private SavingGoalService service;

    @BeforeEach
    void setUp() {
        service = new SavingGoalService(new InMemorySavingGoalMapper(), new InMemoryAllocationGoalMapper());
    }

    private SavingGoal.SavingGoalBuilder validGoal() {
        return SavingGoal.builder()
                .userId(USER_ID)
                .targetMonth(TARGET_MONTH)
                .targetAmount(2_500_000L)
                .laborIntensity(3L);
    }

    @Test
    @DisplayName("저축 목표를 정상 등록한다")
    void register_success() {
        SavingGoal result = service.registerSavingGoal(validGoal().build());

        assertNotNull(result.getSavingGoalId());
        assertEquals(2_500_000L, result.getTargetAmount());
        assertEquals(3L, result.getLaborIntensity());
    }

    @Test
    @DisplayName("target_amount가 0 이하면 등록에 실패한다")
    void register_invalidTargetAmount_throws() {
        SavingGoal invalid = validGoal().targetAmount(0L).build();

        assertThrows(ServiceException.class, () -> service.registerSavingGoal(invalid));
    }

    @Test
    @DisplayName("labor_intensity가 1~5 범위를 벗어나면 등록에 실패한다")
    void register_invalidLaborIntensity_throws() {
        SavingGoal invalid = validGoal().laborIntensity(6L).build();

        assertThrows(ServiceException.class, () -> service.registerSavingGoal(invalid));
    }

    @Test
    @DisplayName("같은 유저가 같은 달에 중복 등록하면 실패한다")
    void register_duplicateMonth_throws() {
        service.registerSavingGoal(validGoal().build());

        SavingGoal duplicate = validGoal().targetAmount(3_000_000L).laborIntensity(2L).build();
        assertThrows(ServiceException.class, () -> service.registerSavingGoal(duplicate));
    }

    @Test
    @DisplayName("같은 유저라도 다른 달이면 등록할 수 있다")
    void register_differentMonth_succeeds() {
        service.registerSavingGoal(validGoal().build());

        SavingGoal nextMonth = validGoal().targetMonth("2026-09").targetAmount(2_800_000L).build();
        SavingGoal result = service.registerSavingGoal(nextMonth);

        assertNotNull(result.getSavingGoalId());
    }

    @Test
    @DisplayName("이번 달 저축목표가 있으면 조회된다")
    void findCurrentMonthGoal_exists_returnsGoal() {
        service.registerSavingGoal(validGoal().targetMonth(CURRENT_MONTH).build());

        SavingGoal result = service.findCurrentMonthGoal(USER_ID);

        assertNotNull(result);
        assertEquals(CURRENT_MONTH, result.getTargetMonth());
    }

    @Test
    @DisplayName("이번 달 저축목표가 없으면 조회 결과는 null이다")
    void findCurrentMonthGoal_notExists_returnsNull() {
        SavingGoal result = service.findCurrentMonthGoal(USER_ID);

        assertNull(result);
    }

    @Test
    @DisplayName("이번 달 저축목표를 정상 수정한다")
    void updateCurrentMonthGoal_success() {
        service.registerSavingGoal(validGoal().targetMonth(CURRENT_MONTH).build());

        SavingGoal result = service.updateCurrentMonthGoal(USER_ID, 3_000_000L, 5L);

        assertEquals(3_000_000L, result.getTargetAmount());
        assertEquals(5L, result.getLaborIntensity());
        assertEquals(CURRENT_MONTH, service.findCurrentMonthGoal(USER_ID).getTargetMonth());
    }

    @Test
    @DisplayName("이번 달 저축목표가 없으면 수정에 실패한다")
    void updateCurrentMonthGoal_notFound_throws() {
        assertThrows(ServiceException.class, () -> service.updateCurrentMonthGoal(USER_ID, 3_000_000L, 5L));
    }

    @Test
    @DisplayName("수정 시에도 target_amount, labor_intensity 검증을 통과해야 한다")
    void updateCurrentMonthGoal_invalidLaborIntensity_throws() {
        service.registerSavingGoal(validGoal().targetMonth(CURRENT_MONTH).build());

        assertThrows(ServiceException.class, () -> service.updateCurrentMonthGoal(USER_ID, 3_000_000L, 6L));
    }
}
