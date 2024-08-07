package com.atkach.ecoflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {
    private String email;
    private String password;
    @Builder.Default
    private Scene scene = Scene.IOT_APP;
    @Builder.Default
    private UserType userType = UserType.ECOFLOW;
}
