package com.ntropy.bff.dto.defense.request;

import com.ntropy.defense.api.dto.command.DefenseModeReleaseCommand;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class DefenseModeReleaseRequest {
    private LocalDate returnDate;

    public DefenseModeReleaseCommand toCommand(Long userId) {
        return new DefenseModeReleaseCommand(userId, returnDate);
    }
}
