package com.atkach.ecoflow.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Formatter;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class SignatureUtil {
    private static byte[] hmacSha256Hex(String key, String data) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);

        return sha256_HMAC.doFinal(data.getBytes());
    }

    private static String toHexString(byte[] bytes) {
        Formatter result = new Formatter();
        try (result) {
            for (var b : bytes) {
                result.format("%02x", b & 0xff);
            }
            return result.toString();
        }
    }

    public static HttpHeaders generateSignature(
            String accessKey,
            String secret,
            Map<String, ?> uriVariables,
            Object body) throws Exception {

        var timestamp = String.format("%d",
                System.currentTimeMillis());
        var nonce = String.format("%06d",
                SecureRandom.getInstanceStrong().nextInt(1000000));
        return generateSignature(accessKey, secret, timestamp, nonce, uriVariables, body);
    }

    public static HttpHeaders generateSignature(
            String accessKey,
            String secret,
            String timestamp,
            String nonce,
            Map<String, ?> uriVariables,
            Object body) throws Exception {

        Map<String, String> parameters;

        if (Objects.nonNull(body)) {
            parameters = AttributeStringGenerator.generateAttributesMap(body);
        } else {
            parameters = new TreeMap<>();
        }

        for (var name : uriVariables.keySet()) {
            parameters.put(name, String.valueOf(uriVariables.get(name)));
        }

        var parametersString = String.join("&", parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new));

        var stringToSign =
                StringUtils.isNotBlank(parametersString) ?
                        String.format("%s&accessKey=%s&nonce=%s&timestamp=%s", parametersString, accessKey, nonce, timestamp) :
                        String.format("accessKey=%s&nonce=%s&timestamp=%s", accessKey, nonce, timestamp);

        var sign = toHexString(hmacSha256Hex(secret, stringToSign));

        HttpHeaders headers = new HttpHeaders();
        headers.set("accessKey", accessKey);
        headers.set("nonce", nonce);
        headers.set("timestamp", timestamp);
        headers.set("sign", sign);

        return headers;
    }
}
