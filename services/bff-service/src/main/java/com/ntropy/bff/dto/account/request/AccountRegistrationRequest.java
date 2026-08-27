package com.ntropy.bff.dto.account.request;

import com.ntropy.account.api.dto.AccountRegistrationCommand;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@ApiModel(description = "계좌 등록 요청. VIRTUAL은 기관코드만, CODEF는 은행 로그인 정보도 필요합니다.")
public class AccountRegistrationRequest {

    @ApiModelProperty(required = true, allowableValues = "VIRTUAL,CODEF", example = "VIRTUAL")
    private String connectionType;

    @ApiModelProperty(required = true, example = "0088")
    private String organizationCode;

    @ApiModelProperty(value = "CODEF 연결에서만 필수", example = "bank-user-id")
    private String bankLoginId;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ApiModelProperty(value = "CODEF 연결에서만 필수. 저장하거나 로그에 남기지 않습니다.")
    private String bankLoginPassword;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ApiModelProperty(value = "기업은행·국민은행 CODEF 연결에서만 필수. YYYYMMDD. 저장하거나 로그에 남기지 않습니다.",
            example = "19900101")
    private String birthDate;

    public AccountRegistrationCommand toCommand() {
        return new AccountRegistrationCommand(
                connectionType, organizationCode, bankLoginId, bankLoginPassword, birthDate
        );
    }
}
