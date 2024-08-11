package com.atkach.ecoflow.dto;

import com.atkach.ecoflow.dto.login.LoginData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class LoginResponse extends AbstractResponse {
    private LoginData data;
}
