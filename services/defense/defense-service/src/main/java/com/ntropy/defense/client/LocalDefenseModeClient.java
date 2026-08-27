package com.ntropy.defense.client;

import com.ntropy.defense.api.client.DefenseModeCommandClient;
import com.ntropy.defense.api.client.DefenseModeQueryClient;
import com.ntropy.defense.api.dto.command.DefenseModeEnterCommand;
import com.ntropy.defense.api.dto.command.DefenseModeReleaseCommand;
import com.ntropy.defense.api.dto.summary.DefenseChecklistSummary;
import com.ntropy.defense.api.dto.summary.DefenseCalendarPeriodSummary;
import com.ntropy.defense.api.dto.summary.DefenseCauseSummary;
import com.ntropy.defense.api.dto.summary.DefenseModeSummary;
import com.ntropy.defense.api.dto.summary.FixedExpenseCheckSummary;
import com.ntropy.defense.api.dto.summary.ExpectedIncomeLossSummary;
import com.ntropy.defense.api.dto.summary.GrowthModeSummary;
import com.ntropy.defense.domain.DefenseMode;
import com.ntropy.defense.domain.DefenseCause;
import com.ntropy.defense.domain.DefenseModeStatus;
import com.ntropy.defense.service.DefenseChecklistCatalog;
import com.ntropy.defense.service.DefenseModeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LocalDefenseModeClient implements DefenseModeCommandClient, DefenseModeQueryClient {
    private final DefenseModeService defenseModeService;

    @Override
    public List<DefenseCauseSummary> getCauses() {
        return java.util.Arrays.stream(DefenseCause.values())
                .filter(DefenseCause::isSelectable)
                .map(cause -> new DefenseCauseSummary(
                        cause.name(), cause.getCauseGroup(), cause.getCauseName(),
                        DefenseChecklistCatalog.findBy(cause).stream()
                                .map(item -> item.getTitle())
                                .collect(Collectors.toList()),
                        cause.getGuideMessage()))
                .collect(Collectors.toList());
    }

    @Override
    public DefenseModeSummary enter(DefenseModeEnterCommand command) {
        return toSummary(defenseModeService.enter(command), null, null, null, null);
    }

    @Override
    public DefenseModeSummary getCurrent(Long userId) {
        DefenseMode defenseMode = defenseModeService.getCurrent(userId);
        if (defenseMode.getStatus() == DefenseModeStatus.SCHEDULED) {
            GrowthModeSummary growthMode = new GrowthModeSummary(
                    false,
                    "DISPLAY_ONLY",
                    "방어모드 시작 전까지 성장모드는 계속 유지됩니다.");
            return toSummary(defenseMode, null, null, growthMode, null);
        }
        FixedExpenseCheckSummary fixedExpenseCheck = defenseModeService.getFixedExpenseCheck(defenseMode);
        ExpectedIncomeLossSummary expectedIncomeLoss = defenseModeService.getExpectedIncomeLoss(defenseMode);
        GrowthModeSummary growthMode = new GrowthModeSummary(
                true,
                "DISPLAY_ONLY",
                "근무 추천과 자동 저축을 잠시 멈춘 상태입니다.");
        return toSummary(
                defenseMode,
                fixedExpenseCheck,
                expectedIncomeLoss,
                growthMode,
                defenseModeService.getCurrentDDay(defenseMode));
    }

    @Override
    public List<DefenseCalendarPeriodSummary> getCalendarPeriods(Long userId, LocalDate from, LocalDate to) {
        return defenseModeService.getCalendarPeriods(userId, from, to).stream()
                .map(mode -> new DefenseCalendarPeriodSummary(
                        mode.getDefenseId(),
                        mode.getUnavailableStartDate(),
                        mode.getStatus() == DefenseModeStatus.RELEASED
                                ? mode.getReturnDate()
                                : mode.getExpectedReturnDate()))
                .collect(Collectors.toList());
    }

    @Override
    public DefenseModeSummary release(Long defenseId, DefenseModeReleaseCommand command) {
        return toSummary(defenseModeService.release(defenseId, command), null, null, null, null);
    }

    private DefenseModeSummary toSummary(
            DefenseMode defenseMode,
            FixedExpenseCheckSummary fixedExpenseCheck,
            ExpectedIncomeLossSummary expectedIncomeLoss,
            GrowthModeSummary growthMode,
            Integer dDayOverride) {
        List<DefenseChecklistSummary> checklist = DefenseChecklistCatalog.findBy(defenseMode.getCauseCode()).stream()
                .map(item -> new DefenseChecklistSummary(item.name(), item.getTitle(), item.getDescription()))
                .collect(Collectors.toList());
        return new DefenseModeSummary(
                defenseMode.getDefenseId(), defenseMode.getUserId(), defenseMode.getCauseCode().name(),
                defenseMode.getCauseCode().getCauseName(),
                defenseMode.getUnavailableStartDate(), defenseMode.getExpectedReturnDate(),
                defenseMode.getReturnDate(), defenseMode.getReserveAmountSnapshot(),
                defenseMode.getSafeAssetAmountSnapshot(), defenseMode.getAvailableAssetsSnapshot(),
                defenseMode.getAverageMonthlyExpense(), defenseMode.getDailyExpense(),
                dDayOverride == null ? defenseMode.getDDay() : dDayOverride,
                defenseMode.getCalculationStatus() == null ? null : defenseMode.getCalculationStatus().name(),
                defenseMode.getStatus().name(),
                defenseMode.getCreatedAt(), checklist, fixedExpenseCheck, expectedIncomeLoss, growthMode);
    }
}
