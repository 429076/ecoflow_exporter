package com.atkach.ecoflow.dto;

import lombok.Data;

@Data
public class AbstractResponse {
    private int code;
    private String message;
    private String eagleEyeTraceId;
    private String tid;
}
