package com.atkach.ecoflow.dto;

import com.atkach.ecoflow.dto.data.DeviceListResponseData;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class DeviceListResponse extends AbstractResponse {
    private List<DeviceListResponseData> data;
}
