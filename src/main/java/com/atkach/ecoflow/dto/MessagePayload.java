package com.atkach.ecoflow.dto;

import lombok.Data;

import java.util.Map;

@Data
public class MessagePayload {
    private String addr;
    private String cmdFunc;
    private String cmdId;
    private String id;
    private String version;
    private String timestamp;
    private String moduleType;
    private Map<String, Object> params;
}
