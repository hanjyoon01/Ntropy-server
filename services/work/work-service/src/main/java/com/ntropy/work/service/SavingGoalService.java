package com.ntropy.work.service;

import java.time.YearMonth;

import org.springframework.stereotype.Service;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.work.domain.entity.SavingGoal;
import com.ntropy.work.exception.WorkErrorCode;
import com.ntropy.work.mapper.SavingGoalMapper;
import com.ntropy.work.mapper.AllocationGoalMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SavingGoalService {

    private static final long MIN_LABOR_INTENSITY = 1;
    private static final long MAX_LABOR_INTENSITY = 5;

    private final SavingGoalMapper savingGoalMapper;
    private final AllocationGoalMapper allocationGoalMapper;

    /**
     * 월별 저축 목표 등록. 같은 유저가 같은 달에 중복 등록할 수 없다.
     */
    public SavingGoal registerSavingGoal(SavingGoal savingGoal) {
        validate(savingGoal);

        SavingGoal existing = savingGoalMapper.findByUserIdAndTargetMonth(
                savingGoal.getUserId(), savingGoal.getTargetMonth());
        if (existing != null) {
            throw new ServiceException(WorkErrorCode.SAVING_GOAL_ALREADY_EXISTS,
                    "userId=" + savingGoal.getUserId() + ", targetMonth=" + savingGoal.getTargetMonth());
        }

        savingGoalMapper.insert(savingGoal);
        return savingGoal;
    }

    /**
     * 이번 달(서버 시간 기준) 저축목표를 조회한다. 등록된 게 없으면 null.
     */
    public SavingGoal findCurrentMonthGoal(Long userId) {
        return savingGoalMapper.findByUserIdAndTargetMonth(userId, currentMonth());
    }

    /**
     * 이번 달(서버 시간 기준) 저축목표를 수정한다. targetMonth는 수정 대상이 아니다.
     * 이번 달에 등록된 게 없으면 예외.
     */
    public SavingGoal updateCurrentMonthGoal(Long userId, Long targetAmount, Long laborIntensity) {
        SavingGoal existing = findCurrentMonthGoal(userId);
        if (existing == null) {
            throw new ServiceException(WorkErrorCode.SAVING_GOAL_NOT_FOUND, "userId=" + userId);
        }

        existing.setTargetAmount(targetAmount);
        existing.setLaborIntensity(laborIntensity);
        validate(existing);

        savingGoalMapper.update(existing);
        // 목표 금액·희망 노동 강도가 바뀌면 이번 달 추천 결과도 다시 계산해야 합니다.
        allocationGoalMapper.deleteByUserIdAndTargetMonth(userId, existing.getTargetMonth());
        return existing;
    }

    private String currentMonth() {
        return YearMonth.now().toString();
    }

    private void validate(SavingGoal savingGoal) {
        if (savingGoal.getTargetAmount() == null || savingGoal.getTargetAmount() <= 0) {
            throw new ServiceException(WorkErrorCode.SAVING_GOAL_INVALID_TARGET_AMOUNT);
        }
        Long laborIntensity = savingGoal.getLaborIntensity();
        if (laborIntensity == null || laborIntensity < MIN_LABOR_INTENSITY || laborIntensity > MAX_LABOR_INTENSITY) {
            throw new ServiceException(WorkErrorCode.SAVING_GOAL_INVALID_LABOR_INTENSITY);
        }
    }
}
