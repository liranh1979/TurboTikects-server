package com.turbotikects.turbotikectsserver.config;

import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Reads ssl_settings on startup via raw JDBC (DataSource is available before JPA)
 * and configures embedded Tomcat for HTTPS if a certificate has been installed.
 *
 * When SSL is active:
 *   - Main connector listens on httpsPort (e.g. 3443) with TLS
 *   - An additional HTTP connector on port 3000 redirects all requests to httpsPort
 */
@Configuration
public class SslBootstrapConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private static final Logger log = LoggerFactory.getLogger(SslBootstrapConfig.class);

    private final DataSource dataSource;

    @Value("${app.attachments.storage-path:./uploads}")
    private String storagePath;

    public SslBootstrapConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT enabled, keystore_path, keystore_password, https_port " +
                     "FROM ssl_settings WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) return;
            if (!rs.getBoolean("enabled")) return;

            String keystoreRelPath = rs.getString("keystore_path");
            String keystorePassword = rs.getString("keystore_password");
            int httpsPort = rs.getInt("https_port");

            if (keystoreRelPath == null || keystorePassword == null) return;

            java.nio.file.Path absoluteKs = Paths.get(storagePath)
                    .toAbsolutePath().normalize()
                    .resolve(keystoreRelPath);

            if (!Files.exists(absoluteKs)) {
                log.warn("SSL enabled in DB but keystore not found at {}; starting on HTTP", absoluteKs);
                return;
            }

            // Configure HTTPS as the main connector
            Ssl ssl = new Ssl();
            ssl.setKeyStoreType("PKCS12");
            ssl.setKeyStore("file:" + absoluteKs.toAbsolutePath());
            ssl.setKeyStorePassword(keystorePassword);
            factory.setSsl(ssl);
            factory.setPort(httpsPort);

            // HTTP connector on port 3000 — redirects to HTTPS
            Connector httpConnector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            httpConnector.setScheme("http");
            httpConnector.setPort(3000);
            httpConnector.setSecure(false);
            httpConnector.setRedirectPort(httpsPort);
            factory.addAdditionalTomcatConnectors(httpConnector);

            log.info("SSL/TLS enabled — HTTPS on port {}, HTTP redirect on port 3000", httpsPort);

        } catch (Exception e) {
            // ssl_settings table may not exist yet (first Flyway run) — safe to ignore, start as HTTP
            log.debug("SSL bootstrap skipped: {}", e.getMessage());
        }
    }
}
