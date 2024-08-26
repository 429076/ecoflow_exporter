package com.atkach.ecoflow.dto.data;

import lombok.Data;

@Data
public class DeviceListResponseData {
    private String sn;
    private String deviceName;
    private String productName;
    private int online;
}
