package com.github.swim_developer.framework.application.service;

import com.github.swim_developer.framework.domain.exception.HashCalculationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractSubscriptionHashCalculator<R> {

    public abstract String calculateHash(R request, String userId);

    protected String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new HashCalculationException("SHA-256 algorithm not available", e);
        }
    }

    protected String sortedListToString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return list.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::toLowerCase)
                .sorted()
                .collect(Collectors.joining(","));
    }

    protected String nullSafe(String value) {
        return value == null ? "" : value.toLowerCase().trim();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
