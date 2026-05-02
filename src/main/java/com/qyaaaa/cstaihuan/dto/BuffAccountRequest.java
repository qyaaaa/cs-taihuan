package com.qyaaaa.cstaihuan.dto;

import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class BuffAccountRequest {
    @Size(max = 64, message = ErrorMessages.BUFF_ACCOUNT_NICKNAME_SIZE)
    private String nickname;

    @Size(max = 128, message = ErrorMessages.BUFF_ACCOUNT_USER_ID_SIZE)
    private String buffUserId;
}
