package com.atkach.ecoflow.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class DeviceQuotaResponse extends AbstractResponse {
    private Map<String, Object> data;
}
