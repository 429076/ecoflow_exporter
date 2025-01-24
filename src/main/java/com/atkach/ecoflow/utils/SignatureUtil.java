package com.atkach.ecoflow.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

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
            List<String> signatureLogs,
            String zoneId,
            String accessKey,
            String secret,
            Map<String, ?> uriVariables,
            Object body) throws Exception {

        var timestamp = String.format("%d",
                ZonedDateTime.now(ZoneId.of(zoneId)).toInstant().toEpochMilli());
        if (signatureLogs != null) signatureLogs.add(timestamp);
        var nonce = String.format("%06d",
                SecureRandom.getInstanceStrong().nextInt(1000000));
        if (signatureLogs != null) signatureLogs.add(nonce);
        return generateSignature(signatureLogs, accessKey, secret, timestamp, nonce, uriVariables, body);
    }

    public static HttpHeaders generateSignature(
            List<String> signatureLogs,
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

        if (signatureLogs != null) signatureLogs.add(stringToSign);

        var sign = toHexString(hmacSha256Hex(secret, stringToSign));

        if (signatureLogs != null) signatureLogs.add(sign);

        HttpHeaders headers = new HttpHeaders();
        headers.set("accessKey", accessKey);
        headers.set("nonce", nonce);
        headers.set("timestamp", timestamp);
        headers.set("sign", sign);

        return headers;
    }
}
