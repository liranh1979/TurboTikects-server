package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.SslInfoDto;
import com.turbotikects.turbotikectsserver.entitys.SslSettingsEntity;
import com.turbotikects.turbotikectsserver.repositorys.SslSettingsRepository;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.Random;

@Service
public class SslSettingsService {

    private static final String KEYSTORE_RELATIVE_PATH = "certs/keystore.p12";
    private static final DateTimeFormatter EXPIRY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SslSettingsRepository sslRepo;
    private final FileStorageService    fileStorage;
    private final CacheManager          cacheManager;

    @Value("${app.attachments.storage-path:./uploads}")
    private String storagePath;

    public SslSettingsService(SslSettingsRepository sslRepo,
                               FileStorageService fileStorage,
                               CacheManager cacheManager) {
        this.sslRepo      = sslRepo;
        this.fileStorage  = fileStorage;
        this.cacheManager = cacheManager;
    }

    public SslInfoDto getInfo() {
        return sslRepo.findById(1).map(this::toDto).orElseGet(SslInfoDto::new);
    }

    @Transactional
    public SslInfoDto uploadCertificate(String certType, String domain, int httpsPort,
                                         MultipartFile certFile, MultipartFile keyFile,
                                         MultipartFile p12File,  String p12Password) {
        KeyStore ks;
        String   keystorePassword = generatePassword();

        boolean hasPem   = certFile != null && !certFile.isEmpty() && keyFile != null && !keyFile.isEmpty();
        boolean hasPkcs12 = p12File != null && !p12File.isEmpty();

        if (!hasPem && !hasPkcs12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide either PEM cert+key files or a PKCS12 bundle");
        }

        X509Certificate cert;
        if (hasPem) {
            ks   = buildKeystoreFromPem(certFile, keyFile, keystorePassword);
            cert = extractCertFromKeystore(ks, keystorePassword);
        } else {
            ks   = loadPkcs12(p12File, p12Password, keystorePassword);
            cert = extractCertFromKeystore(ks, keystorePassword);
        }

        byte[] ksBytes = serializeKeystore(ks, keystorePassword);
        try {
            fileStorage.store(new ByteArrayInputStream(ksBytes), KEYSTORE_RELATIVE_PATH, ksBytes.length);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store keystore: " + e.getMessage());
        }

        SslSettingsEntity entity = sslRepo.findById(1).orElse(new SslSettingsEntity());
        entity.setEnabled(true);
        entity.setCertType(certType);
        entity.setDomain(domain != null ? domain.trim() : null);
        entity.setHttpsPort(httpsPort > 0 ? httpsPort : 3443);
        entity.setKeystorePath(KEYSTORE_RELATIVE_PATH);
        entity.setKeystorePassword(keystorePassword);
        entity.setCertSubject(cert.getSubjectX500Principal().getName());
        entity.setCertIssuer(cert.getIssuerX500Principal().getName());
        entity.setCertExpiry(cert.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        sslRepo.save(entity);

        clearAllSessions();
        scheduleRestart();
        return toDto(entity);
    }

    @Transactional
    public void removeCertificate() {
        SslSettingsEntity entity = sslRepo.findById(1).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No SSL configuration found"));

        entity.setEnabled(false);
        entity.setCertType(null);
        entity.setDomain(null);
        entity.setKeystorePath(null);
        entity.setKeystorePassword(null);
        entity.setCertSubject(null);
        entity.setCertIssuer(null);
        entity.setCertExpiry(null);
        sslRepo.save(entity);

        if (fileStorage.exists(KEYSTORE_RELATIVE_PATH)) {
            try { fileStorage.delete(KEYSTORE_RELATIVE_PATH); } catch (IOException ignored) {}
        }

        clearAllSessions();
        scheduleRestart();
    }

    // ── PEM processing ──────────────────────────────────────────────────────

    private KeyStore buildKeystoreFromPem(MultipartFile certFile, MultipartFile keyFile, String ksPassword) {
        try {
            X509Certificate cert = parsePemCert(certFile);
            PrivateKey      key  = parsePemPrivateKey(keyFile);

            verifyCertNotExpired(cert);
            verifyKeyMatchesCert(key, cert);

            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setKeyEntry("server", key, ksPassword.toCharArray(), new java.security.cert.Certificate[]{cert});
            return ks;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ssl_error_invalid_cert: " + e.getMessage());
        }
    }

    private X509Certificate parsePemCert(MultipartFile file) throws Exception {
        try (InputStream in = file.getInputStream()) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(in);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ssl_error_invalid_cert");
        }
    }

    private PrivateKey parsePemPrivateKey(MultipartFile file) throws Exception {
        try (Reader reader = new InputStreamReader(file.getInputStream());
             PEMParser parser = new PEMParser(reader)) {

            Object obj = parser.readObject();
            if (obj == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ssl_error_invalid_cert");
            }

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (obj instanceof PEMKeyPair) {
                return converter.getPrivateKey(((PEMKeyPair) obj).getPrivateKeyInfo());
            } else if (obj instanceof PrivateKeyInfo) {
                return converter.getPrivateKey((PrivateKeyInfo) obj);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ssl_error_invalid_cert");
            }
        }
    }

    // ── PKCS12 processing ───────────────────────────────────────────────────

    private KeyStore loadPkcs12(MultipartFile p12File, String p12Password, String newPassword) {
        try {
            char[] pwd = p12Password != null ? p12Password.toCharArray() : new char[0];
            KeyStore src = KeyStore.getInstance("PKCS12");
            src.load(p12File.getInputStream(), pwd);

            // Verify at least one private key entry exists
            boolean hasKey = false;
            Enumeration<String> aliases = src.aliases();
            while (aliases.hasMoreElements()) {
                if (src.isKeyEntry(aliases.nextElement())) { hasKey = true; break; }
            }
            if (!hasKey) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ssl_error_bad_pkcs12");
            }

            // Re-serialize with our internal password so we own it
            KeyStore dst = KeyStore.getInstance("PKCS12");
            dst.load(null, null);
            Enumeration<String> aliases2 = src.aliases();
            while (aliases2.hasMoreElements()) {
                String alias = aliases2.nextElement();
                if (src.isKeyEntry(alias)) {
                    Key key = src.getKey(alias, pwd);
                    java.security.cert.Certificate[] chain = src.getCertificateChain(alias);
                    X509Certificate leaf = (X509Certificate) chain[0];
                    verifyCertNotExpired(leaf);
                    dst.setKeyEntry("server", key, newPassword.toCharArray(), chain);
                    break;
                }
            }
            return dst;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ssl_error_bad_pkcs12");
        }
    }

    // ── Validation helpers ──────────────────────────────────────────────────

    private void verifyCertNotExpired(X509Certificate cert) {
        if (cert.getNotAfter().toInstant().isBefore(java.time.Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ssl_error_cert_expired");
        }
    }

    private void verifyKeyMatchesCert(PrivateKey key, X509Certificate cert) {
        try {
            String algorithm = cert.getPublicKey().getAlgorithm();
            String sigAlg    = "EC".equalsIgnoreCase(algorithm) ? "SHA256withECDSA" : "SHA256withRSA";
            byte[] data = new byte[16];
            new Random().nextBytes(data);

            Signature signer = Signature.getInstance(sigAlg);
            signer.initSign(key);
            signer.update(data);
            byte[] sig = signer.sign();

            Signature verifier = Signature.getInstance(sigAlg);
            verifier.initVerify(cert.getPublicKey());
            verifier.update(data);
            if (!verifier.verify(sig)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ssl_error_key_mismatch");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ssl_error_key_mismatch");
        }
    }

    // ── Keystore serialization ──────────────────────────────────────────────

    private byte[] serializeKeystore(KeyStore ks, String password) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ks.store(out, password.toCharArray());
            return out.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize keystore");
        }
    }

    private X509Certificate extractCertFromKeystore(KeyStore ks, String password) {
        try {
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (ks.isKeyEntry(alias)) {
                    java.security.cert.Certificate[] chain = ks.getCertificateChain(alias);
                    if (chain != null && chain.length > 0) {
                        return (X509Certificate) chain[0];
                    }
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ssl_error_invalid_cert");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read keystore");
        }
    }

    // ── Session clearing + restart ──────────────────────────────────────────

    private void clearAllSessions() {
        var cache = cacheManager.getCache("sessions");
        if (cache != null) cache.clear();
    }

    private void scheduleRestart() {
        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            System.exit(0);
        }, "ssl-restart").start();
    }

    // ── DTO mapping ─────────────────────────────────────────────────────────

    private SslInfoDto toDto(SslSettingsEntity e) {
        SslInfoDto dto = new SslInfoDto();
        dto.setEnabled(e.isEnabled());
        dto.setCertType(e.getCertType());
        dto.setDomain(e.getDomain());
        dto.setHttpsPort(e.getHttpsPort());
        dto.setCertSubject(e.getCertSubject());
        dto.setCertIssuer(e.getCertIssuer());
        if (e.getCertExpiry() != null) {
            dto.setCertExpiry(e.getCertExpiry().format(EXPIRY_FMT));
        }
        if (e.getCreatedAt() != null) {
            dto.setInstalledAt(e.getCreatedAt().format(EXPIRY_FMT));
        }
        return dto;
    }

    private String generatePassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(32);
        SecureRandom rng = new SecureRandom();
        for (int i = 0; i < 32; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }
}
