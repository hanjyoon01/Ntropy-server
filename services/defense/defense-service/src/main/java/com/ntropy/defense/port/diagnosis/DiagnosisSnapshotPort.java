package com.ntropy.defense.port.diagnosis;

/** defense-service가 정의한, diagnosis-service의 D-Day 계산용 재무 스냅샷 조회 포트. */
@FunctionalInterface
public interface DiagnosisSnapshotPort {

    DefenseDiagnosisSnapshot getDefenseSnapshot(Long userId);
}
