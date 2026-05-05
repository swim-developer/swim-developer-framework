package com.github.swim_developer.framework.integration.tls;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public final class TlsTestCertificateGenerator {

    private static final String BC = BouncyCastleProvider.PROVIDER_NAME;
    private static final String SIGNATURE_ALGORITHM = "SHA256WithRSAEncryption";
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final int KEY_SIZE = 2048;
    private static final X500Name CA_DN = new X500Name("CN=SWIM Test CA,O=SWIM Test,C=EU");

    static {
        if (Security.getProvider(BC) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final Path outputDir;
    private KeyPair caKeyPair;
    private X509Certificate caCert;
    private KeyPair serverKeyPair;
    private X509Certificate serverCert;
    private KeyPair clientKeyPair;
    private X509Certificate clientCert;
    private KeyPair revokedClientKeyPair;
    private X509Certificate revokedClientCert;
    private X509CRL crl;

    public TlsTestCertificateGenerator(Path outputDir) {
        this.outputDir = outputDir;
    }

    public static TlsTestCertificateGenerator generateAll() throws Exception {
        Path dir = Files.createTempDirectory("swim-tls-test-");
        TlsTestCertificateGenerator gen = new TlsTestCertificateGenerator(dir);
        gen.generate();
        return gen;
    }

    public void generate() throws Exception {
        caKeyPair = generateKeyPair();
        caCert = generateCaCertificate(caKeyPair);

        serverKeyPair = generateKeyPair();
        serverCert = generateServerCertificate(serverKeyPair, caKeyPair, caCert);

        clientKeyPair = generateKeyPair();
        clientCert = generateClientCertificate(clientKeyPair, caKeyPair, caCert, "CN=swim-client,O=SWIM Test");

        revokedClientKeyPair = generateKeyPair();
        revokedClientCert = generateClientCertificate(revokedClientKeyPair, caKeyPair, caCert, "CN=swim-revoked-client,O=SWIM Test");

        crl = generateCrl(caKeyPair, revokedClientCert);

        writeKeystore("broker-keystore.p12", serverKeyPair, serverCert, caCert);
        writeKeystore("client-keystore.p12", clientKeyPair, clientCert, caCert);
        writeKeystore("revoked-client-keystore.p12", revokedClientKeyPair, revokedClientCert, caCert);
        writeTruststore("truststore.p12", caCert);
        writeCrl("ca.crl", crl);
    }

    public Path getOutputDir() { return outputDir; }
    public String getKeystorePassword() { return KEYSTORE_PASSWORD; }
    public Path getBrokerKeystorePath() { return outputDir.resolve("broker-keystore.p12"); }
    public Path getClientKeystorePath() { return outputDir.resolve("client-keystore.p12"); }
    public Path getRevokedClientKeystorePath() { return outputDir.resolve("revoked-client-keystore.p12"); }
    public Path getTruststorePath() { return outputDir.resolve("truststore.p12"); }
    public Path getCrlPath() { return outputDir.resolve("ca.crl"); }
    public X509Certificate getCaCert() { return caCert; }
    public X509Certificate getServerCert() { return serverCert; }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BC);
        kpg.initialize(KEY_SIZE);
        return kpg.generateKeyPair();
    }

    private X509Certificate generateCaCertificate(KeyPair caKp) throws Exception {
        Instant now = Instant.now();
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                CA_DN,
                BigInteger.valueOf(1),
                Date.from(now),
                Date.from(now.plus(365, ChronoUnit.DAYS)),
                CA_DN,
                caKp.getPublic());

        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(caKp.getPublic()));

        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(BC).build(caKp.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().setProvider(BC).getCertificate(holder);
    }

    private X509Certificate generateServerCertificate(KeyPair serverKp, KeyPair caKp, X509Certificate ca) throws Exception {
        Instant now = Instant.now();
        X500Name subject = new X500Name("CN=localhost,O=SWIM Test");
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                CA_DN,
                BigInteger.valueOf(2),
                Date.from(now),
                Date.from(now.plus(365, ChronoUnit.DAYS)),
                subject,
                serverKp.getPublic());

        builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
        builder.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.dNSName, "localhost")));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(serverKp.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                extUtils.createAuthorityKeyIdentifier(ca));

        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(BC).build(caKp.getPrivate());
        return new JcaX509CertificateConverter().setProvider(BC).getCertificate(builder.build(signer));
    }

    private X509Certificate generateClientCertificate(KeyPair clientKp, KeyPair caKp, X509Certificate ca, String dn) throws Exception {
        Instant now = Instant.now();
        X500Name subject = new X500Name(dn);
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

        long serial = System.nanoTime();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                CA_DN,
                BigInteger.valueOf(serial),
                Date.from(now),
                Date.from(now.plus(365, ChronoUnit.DAYS)),
                subject,
                clientKp.getPublic());

        builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(clientKp.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                extUtils.createAuthorityKeyIdentifier(ca));

        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(BC).build(caKp.getPrivate());
        return new JcaX509CertificateConverter().setProvider(BC).getCertificate(builder.build(signer));
    }

    private X509CRL generateCrl(KeyPair caKp, X509Certificate revokedCert) throws Exception {
        Instant now = Instant.now();

        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(CA_DN, Date.from(now));
        crlBuilder.setNextUpdate(Date.from(now.plus(365, ChronoUnit.DAYS)));
        crlBuilder.addCRLEntry(revokedCert.getSerialNumber(), Date.from(now), CRLReason.keyCompromise);

        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(BC).build(caKp.getPrivate());
        X509CRLHolder crlHolder = crlBuilder.build(signer);
        return new JcaX509CRLConverter().setProvider(BC).getCRL(crlHolder);
    }

    private void writeKeystore(String filename, KeyPair keyPair, X509Certificate cert, X509Certificate ca) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("key", keyPair.getPrivate(), KEYSTORE_PASSWORD.toCharArray(),
                new X509Certificate[]{cert, ca});
        Path path = outputDir.resolve(filename);
        try (OutputStream os = new FileOutputStream(path.toFile())) {
            ks.store(os, KEYSTORE_PASSWORD.toCharArray());
        }
    }

    private void writeTruststore(String filename, X509Certificate ca) throws Exception {
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        ts.setCertificateEntry("ca", ca);
        Path path = outputDir.resolve(filename);
        try (OutputStream os = new FileOutputStream(path.toFile())) {
            ts.store(os, KEYSTORE_PASSWORD.toCharArray());
        }
    }

    private void writeCrl(String filename, X509CRL crlData) throws Exception {
        Path path = outputDir.resolve(filename);
        Files.write(path, crlData.getEncoded());
    }
}
