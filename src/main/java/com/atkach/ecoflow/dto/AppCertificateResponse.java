package com.atkach.ecoflow.dto;

import com.atkach.ecoflow.dto.data.AppCertificateResponseData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AppCertificateResponse extends AbstractResponse {
    private AppCertificateResponseData data;
}
