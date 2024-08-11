package com.atkach.ecoflow.dto.cert;

import lombok.Data;

@Data
public class AppCertificateResponseData {
    private String url;
    private int port;
    private String protocol;
    private String certificateAccount;
    private String certificatePassword;
}
