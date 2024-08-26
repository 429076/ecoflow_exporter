package com.atkach.ecoflow.dto;

import lombok.Data;

import java.util.Map;

@Data
public class MessagePayload {
    private String id;
    private String version;
    private String typeCode;

    private String cmdId;
    private String cmdFunc;
    private String addr;

    private int moduleType;
    private int needAck;
    private long time;

    private Map<String, Object> params;
}
