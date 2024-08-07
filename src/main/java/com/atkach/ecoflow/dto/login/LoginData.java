package com.atkach.ecoflow.dto.login;

import lombok.Data;

@Data
public class LoginData {
    private User user;
    private String token;
}
