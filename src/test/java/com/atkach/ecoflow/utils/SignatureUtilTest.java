package com.atkach.ecoflow.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Objects;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class SignatureUtilTest {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Root {
        private String sn;
        private Params params;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Params {
        private int cmdSet;
        private int id;
        private int eps;
    }

    @Test
    public void testSignature() throws Exception {
        var headers = SignatureUtil.generateSignature(
                null,
                "Fp4SvIprYSDPXtYJidEtUAd1o",
                "WIbFEKre0s6sLnh4ei7SPUeYnptHG6V",
                "1671171709428",
                "345164",
                new HashMap<>(),
                Root.builder()
                        .sn("123456789")
                        .params(
                                Params.builder()
                                        .cmdSet(11)
                                        .id(24)
                                        .eps(0)
                                        .build()
                        )
                        .build()
        );

        assertThat(Objects.requireNonNull(headers.get("sign")).get(0)).isEqualTo("07c13b65e037faf3b153d51613638fa80003c4c38d2407379a7f52851af1473e");
    }
}
