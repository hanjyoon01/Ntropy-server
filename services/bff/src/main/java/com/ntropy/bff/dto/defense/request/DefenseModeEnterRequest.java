package com.ntropy.bff.dto.defense.request;

import com.ntropy.defense.api.dto.command.DefenseModeEnterCommand;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class DefenseModeEnterRequest {
    private String causeCode;
    private LocalDate unavailableStartDate;
    private LocalDate expectedReturnDate;

    public DefenseModeEnterCommand toCommand(Long userId) {
        return new DefenseModeEnterCommand(userId, causeCode, unavailableStartDate, expectedReturnDate);
    }
}
