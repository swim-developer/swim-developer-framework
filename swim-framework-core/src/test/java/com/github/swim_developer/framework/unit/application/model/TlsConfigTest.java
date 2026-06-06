package com.github.swim_developer.framework.unit.application.model;

import com.github.swim_developer.framework.application.model.TlsConfig;
import com.github.swim_developer.framework.application.model.TlsKeystoreType;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("application")
@ExtendWith(TestNameLoggerExtension.class)
class TlsConfigTest {

    @Test
    void effectiveCrlPaths_returnsListWhenExplicitlyConfigured() {
        TlsConfig config = TlsConfig.builder()
                .keystoreType(TlsKeystoreType.JKS)
                .crlPaths(List.of("/certs/crl1.pem", "/certs/crl2.pem"))
                .build();

        assertThat(config.effectiveCrlPaths())
                .containsExactly("/certs/crl1.pem", "/certs/crl2.pem");
    }

    @Test
    void effectiveCrlPaths_fallsBackToSingleCrlPathWhenListIsEmpty() {
        TlsConfig config = TlsConfig.builder()
                .keystoreType(TlsKeystoreType.PEM)
                .crlPath("/certs/crl.pem")
                .crlPaths(List.of())
                .build();

        assertThat(config.effectiveCrlPaths()).containsExactly("/certs/crl.pem");
    }

    @Test
    void effectiveCrlPaths_returnsEmptyWhenNoCrlConfigured() {
        TlsConfig config = TlsConfig.builder().keystoreType(TlsKeystoreType.JKS).build();

        assertThat(config.effectiveCrlPaths()).isEmpty();
    }

    @Test
    void effectiveCrlPaths_ignoresBlankCrlPath() {
        TlsConfig config = TlsConfig.builder()
                .keystoreType(TlsKeystoreType.JKS)
                .crlPath("   ")
                .build();

        assertThat(config.effectiveCrlPaths()).isEmpty();
    }

    @Test
    void allCertificateFiles_includesTrustStoreAndKeyStore() {
        TlsConfig config = TlsConfig.builder()
                .keystoreType(TlsKeystoreType.JKS)
                .trustStorePath("/certs/truststore.jks")
                .keyStorePath("/certs/keystore.jks")
                .build();

        assertThat(config.allCertificateFiles())
                .containsExactlyInAnyOrder("/certs/truststore.jks", "/certs/keystore.jks");
    }

    @Test
    void allCertificateFiles_includesPemCertAndKey() {
        TlsConfig config = TlsConfig.builder()
                .keystoreType(TlsKeystoreType.PEM)
                .certPath("/certs/client.crt")
                .keyPath("/certs/client.key")
                .build();

        assertThat(config.allCertificateFiles())
                .containsExactlyInAnyOrder("/certs/client.crt", "/certs/client.key");
    }

    @Test
    void allCertificateFiles_includesCrlPaths() {
        TlsConfig config = TlsConfig.builder()
                .keystoreType(TlsKeystoreType.PEM)
                .certPath("/certs/client.crt")
                .crlPaths(List.of("/certs/crl.pem"))
                .build();

        assertThat(config.allCertificateFiles())
                .contains("/certs/client.crt", "/certs/crl.pem");
    }

    @Test
    void allCertificateFiles_isUnmodifiable() {
        TlsConfig config = TlsConfig.builder()
                .keystoreType(TlsKeystoreType.JKS)
                .trustStorePath("/certs/trust.jks")
                .build();

        List<String> files = config.allCertificateFiles();
        assertThatThrownBy(() -> files.add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allCertificateFiles_skipsNullAndBlankPaths() {
        TlsConfig config = TlsConfig.builder()
                .keystoreType(TlsKeystoreType.JKS)
                .trustStorePath(null)
                .keyStorePath("  ")
                .certPath("/certs/valid.pem")
                .build();

        assertThat(config.allCertificateFiles()).containsExactly("/certs/valid.pem");
    }

    @Test
    void allCertificateFiles_returnsEmptyWhenNothingConfigured() {
        TlsConfig config = TlsConfig.builder().keystoreType(TlsKeystoreType.JKS).build();

        assertThat(config.allCertificateFiles()).isEmpty();
    }
}
