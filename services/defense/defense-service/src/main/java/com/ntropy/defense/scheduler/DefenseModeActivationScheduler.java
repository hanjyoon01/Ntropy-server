package com.ntropy.defense.scheduler;

import com.ntropy.defense.service.DefenseModeService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class DefenseModeActivationScheduler {
    private static final Logger LOGGER = Logger.getLogger(DefenseModeActivationScheduler.class.getName());

    private final DefenseModeService defenseModeService;

    @Scheduled(
            cron = "${defense.scheduler.activation-cron:0 5 0 * * ?}",
            zone = "Asia/Seoul"
    )
    public void activateScheduledModes() {
        try {
            int activatedCount = defenseModeService.activateScheduledModes();
            if (activatedCount > 0) {
                LOGGER.info("[방어모드 배치] 예약 방어모드 " + activatedCount + "건 활성화 완료");
            }
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "[방어모드 배치] 예약 방어모드 활성화 중 오류 발생", exception);
        }
    }
}
