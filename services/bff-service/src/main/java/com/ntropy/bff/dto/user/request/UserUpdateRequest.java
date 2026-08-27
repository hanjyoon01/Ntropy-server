package com.ntropy.bff.dto.user.request;

import com.ntropy.user.api.dto.UserUpdateCommand;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserUpdateRequest {

    private String name;
    private String email;
    private Boolean alarmAgree;
    private Boolean locationAgree;

    public UserUpdateCommand toCommand() {
        return new UserUpdateCommand(name, email, alarmAgree, locationAgree);
    }
}
