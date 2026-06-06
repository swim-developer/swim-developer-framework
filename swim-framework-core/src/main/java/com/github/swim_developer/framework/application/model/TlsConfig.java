package com.github.swim_developer.framework.application.model;

import lombok.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Builder
public record TlsConfig(
    TlsKeystoreType keystoreType,
    String trustStorePath,
    String trustStorePassword,
    String keyStorePath,
    String keyStorePassword,
    String certPath,
    String keyPath,
    boolean enableRevocationCheck,
    String crlPath,
    List<String> crlPaths,
    boolean ocspEnabled
) {

    public List<String> effectiveCrlPaths() {
        if (crlPaths != null && !crlPaths.isEmpty()) {
            return crlPaths;
        }
        if (crlPath != null && !crlPath.isBlank()) {
            return List.of(crlPath);
        }
        return Collections.emptyList();
    }

    public List<String> allCertificateFiles() {
        List<String> files = new ArrayList<>();
        if (trustStorePath != null && !trustStorePath.isBlank()) files.add(trustStorePath);
        if (keyStorePath != null && !keyStorePath.isBlank()) files.add(keyStorePath);
        if (certPath != null && !certPath.isBlank()) files.add(certPath);
        if (keyPath != null && !keyPath.isBlank()) files.add(keyPath);
        files.addAll(effectiveCrlPaths());
        return Collections.unmodifiableList(files);
    }
}
