package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.entitys.AzureConfigEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.AzureConfigRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SamlService {

    private static final Logger log = LoggerFactory.getLogger(SamlService.class);

    // ?appid= requests the app-specific SAML signing cert, not the tenant-level WS-Fed certs
    private static final String FEDERATION_METADATA_URL =
            "https://login.microsoftonline.com/%s/federationmetadata/2007-06/federationmetadata.xml?appid=%s";
    private static final String METADATA_NS = "urn:oasis:names:tc:SAML:2.0:metadata";
    private static final String DSIG_NS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String ASSERTION_NS = "urn:oasis:names:tc:SAML:2.0:assertion";

    // Cache IdP metadata per tenantId to avoid repeated remote fetches
    private final Map<String, IdpMetadata> metadataCache = new ConcurrentHashMap<>();

    @Value("${app.saml.sp-base-url}")
    private String spBaseUrl;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final AzureConfigRepository configRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final HttpClient httpClient;

    public SamlService(AzureConfigRepository configRepository,
                       UserRepository userRepository,
                       UserService userService) {
        this.configRepository = configRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.httpClient = HttpClient.newHttpClient();
    }

    // ── Public URL helpers ────────────────────────────────────────────────────

    public String getAcsUrl(Long configId) {
        return spBaseUrl + "/saml2/sso/" + configId;
    }

    public String getLoginUrl(Long configId) {
        return spBaseUrl + "/saml2/login/" + configId;
    }

    public String getMetadataUrl(Long configId) {
        return spBaseUrl + "/saml2/metadata/" + configId;
    }

    public String getSpEntityId(AzureConfigEntity config) {
        if (config.getSamlSpEntityId() != null && !config.getSamlSpEntityId().isBlank()) {
            return config.getSamlSpEntityId();
        }
        return "urn:turbotikects:azure:" + config.getId();
    }

    // ── Login initiation (HTTP-Redirect binding) ──────────────────────────────

    public void initiateSamlLogin(Long configId, HttpServletResponse response) throws Exception {
        AzureConfigEntity config = configRepository.findById(configId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SSO config not found"));

        if (!config.isSsoEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SSO not enabled for this config");
        }

        IdpMetadata idp = getIdpMetadata(config);
        String requestId = "_" + UUID.randomUUID().toString().replace("-", "");
        String issueInstant = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        String acsUrl = getAcsUrl(configId);
        String spEntityId = getSpEntityId(config);

        String authnRequest = String.format(
                "<samlp:AuthnRequest" +
                " xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\"" +
                " xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\"" +
                " ID=\"%s\"" +
                " Version=\"2.0\"" +
                " IssueInstant=\"%s\"" +
                " Destination=\"%s\"" +
                " AssertionConsumerServiceURL=\"%s\"" +
                " ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\">" +
                "<saml:Issuer>%s</saml:Issuer>" +
                "<samlp:NameIDPolicy Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress\"" +
                " AllowCreate=\"true\"/>" +
                "</samlp:AuthnRequest>",
                requestId,
                issueInstant,
                xmlEscape(idp.ssoUrl()),
                xmlEscape(acsUrl),
                xmlEscape(spEntityId));

        byte[] deflated = deflate(authnRequest.getBytes(StandardCharsets.UTF_8));
        String samlParam = URLEncoder.encode(Base64.getEncoder().encodeToString(deflated), StandardCharsets.UTF_8);
        String relayState = URLEncoder.encode(configId.toString(), StandardCharsets.UTF_8);

        String redirectUrl = idp.ssoUrl() + "?SAMLRequest=" + samlParam + "&RelayState=" + relayState;
        response.sendRedirect(redirectUrl);
    }

    // ── ACS endpoint (HTTP-POST binding) ─────────────────────────────────────

    public void processSamlResponse(Long configId, String samlResponseBase64, HttpServletResponse response)
            throws Exception {
        AzureConfigEntity config = configRepository.findById(configId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SSO config not found"));

        IdpMetadata idp = getIdpMetadata(config);

        byte[] responseBytes = Base64.getDecoder().decode(samlResponseBase64);
        Document doc = parseXml(responseBytes);

        // Validate XML signature (try all trusted certs — Azure AD rotates signing keys)
        validateXmlSignature(doc, idp.certificates());

        // Extract NameID (usually the user's email / UPN)
        String nameId = extractText(doc, ASSERTION_NS, "NameID");
        if (nameId == null || nameId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No NameID in SAMLResponse");
        }

        // Extract Azure Object Identifier and display name from SAML attributes
        String azureUserId = null;
        String displayName = null;
        NodeList attrStmts = doc.getElementsByTagNameNS(ASSERTION_NS, "AttributeStatement");
        if (attrStmts.getLength() > 0) {
            NodeList attrs = ((Element) attrStmts.item(0)).getElementsByTagNameNS(ASSERTION_NS, "Attribute");
            for (int i = 0; i < attrs.getLength(); i++) {
                Element attr = (Element) attrs.item(i);
                String attrName = attr.getAttribute("Name");
                String attrValue = getFirstChildText(attr, ASSERTION_NS, "AttributeValue");
                if ("http://schemas.microsoft.com/identity/claims/objectidentifier".equals(attrName)) {
                    azureUserId = attrValue;
                } else if ("http://schemas.microsoft.com/identity/claims/displayname".equals(attrName)
                        || "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname".equals(attrName)) {
                    if (displayName == null) displayName = attrValue;
                }
            }
        }

        String username = normalizeEmail(nameId);
        UserEntity user = findOrCreateSamlUser(username, nameId, azureUserId, displayName);

        // Create session and set cookie
        UserDto userDto = userService.loginByEntity(user);
        String sessionId = UUID.randomUUID().toString();
        Cookie cookie = new Cookie("SESSION_ID", sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60);
        response.addCookie(cookie);
        userService.saveSession(sessionId, userDto);

        response.sendRedirect(frontendUrl);
    }

    // ── Metadata test ────────────────────────────────────────────────────────

    public Map<String, Object> testMetadata(Long configId) {
        AzureConfigEntity config = configRepository.findById(configId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SSO config not found"));
        // Remove cached entry so we always do a fresh fetch for the test
        metadataCache.remove(config.getTenantId());
        try {
            IdpMetadata meta = getIdpMetadata(config);
            return Map.of("success", true, "entityId", meta.entityId(), "ssoUrl", meta.ssoUrl());
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }

    // ── SP Metadata XML ───────────────────────────────────────────────────────

    public String getSpMetadataXml(Long configId) {
        AzureConfigEntity config = configRepository.findById(configId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SSO config not found"));
        String spEntityId = xmlEscape(getSpEntityId(config));
        String acsUrl = xmlEscape(getAcsUrl(configId));

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<md:EntityDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\" entityID=\"" + spEntityId + "\">" +
                "<md:SPSSODescriptor AuthnRequestsSigned=\"false\" WantAssertionsSigned=\"true\"" +
                " protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">" +
                "<md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</md:NameIDFormat>" +
                "<md:AssertionConsumerService" +
                " Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\"" +
                " Location=\"" + acsUrl + "\"" +
                " index=\"1\"/>" +
                "</md:SPSSODescriptor>" +
                "</md:EntityDescriptor>";
    }

    // ── IdP metadata fetch & cache ────────────────────────────────────────────

    public IdpMetadata getIdpMetadata(AzureConfigEntity config) {
        String cacheKey = config.getTenantId() + ":" + config.getClientId();
        return metadataCache.computeIfAbsent(cacheKey, key -> {
            try {
                return fetchIdpMetadata(config.getTenantId(), config.getClientId());
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch IdP metadata for tenant " + config.getTenantId(), e);
            }
        });
    }

    private IdpMetadata fetchIdpMetadata(String tenantId, String clientId) throws Exception {
        String url = String.format(FEDERATION_METADATA_URL, tenantId, clientId);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<byte[]> httpResp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (httpResp.statusCode() != 200) {
            throw new RuntimeException("IdP metadata fetch failed: HTTP " + httpResp.statusCode());
        }

        Document doc = parseXml(httpResp.body());

        String entityId = doc.getDocumentElement().getAttribute("entityID");

        // Find HTTP-Redirect SSO endpoint
        String ssoUrl = null;
        NodeList ssoServices = doc.getElementsByTagNameNS(METADATA_NS, "SingleSignOnService");
        for (int i = 0; i < ssoServices.getLength(); i++) {
            Element el = (Element) ssoServices.item(i);
            if ("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect".equals(el.getAttribute("Binding"))) {
                ssoUrl = el.getAttribute("Location");
                break;
            }
        }
        if (ssoUrl == null) {
            throw new RuntimeException("No HTTP-Redirect SSO endpoint in IdP metadata");
        }

        // Collect ALL signing certificates — Azure AD rotates keys and may sign with any of them
        List<X509Certificate> certs = new ArrayList<>();
        NodeList keyDescriptors = doc.getElementsByTagNameNS(METADATA_NS, "KeyDescriptor");
        for (int i = 0; i < keyDescriptors.getLength(); i++) {
            Element kd = (Element) keyDescriptors.item(i);
            String use = kd.getAttribute("use");
            if (use.isBlank() || "signing".equals(use)) {
                NodeList certNodes = kd.getElementsByTagNameNS(DSIG_NS, "X509Certificate");
                for (int j = 0; j < certNodes.getLength(); j++) {
                    certs.add(decodeCert(certNodes.item(j).getTextContent()));
                }
            }
        }
        if (certs.isEmpty()) {
            throw new RuntimeException("No signing certificate in IdP metadata");
        }

        return new IdpMetadata(entityId, ssoUrl, certs);
    }

    private X509Certificate decodeCert(String base64) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(base64.replaceAll("\\s+", ""));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(bytes));
    }

    // ── XML signature validation ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void validateXmlSignature(Document doc, List<X509Certificate> trustedCerts) throws Exception {
        registerIdAttributes(doc.getDocumentElement());

        NodeList signatures = doc.getElementsByTagNameNS(DSIG_NS, "Signature");
        if (signatures.getLength() == 0) {
            throw new SecurityException("No XML signature found in SAMLResponse");
        }

        Element sigElement = (Element) signatures.item(0);

        // Try certs from IdP federation metadata first
        for (X509Certificate cert : trustedCerts) {
            try {
                DOMValidateContext ctx = new DOMValidateContext(cert.getPublicKey(), sigElement);
                XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
                XMLSignature sig = factory.unmarshalXMLSignature(ctx);
                if (sig.validate(ctx)) {
                    log.debug("SAML signature validated with metadata cert: {}", cert.getSubjectX500Principal());
                    return;
                }
            } catch (Exception ignored) {}
        }

        // Azure AD Enterprise Apps use a per-app signing cert that is NOT in the tenant
        // federation metadata. Azure AD always embeds the actual signing cert in each
        // SAMLResponse inside <Signature><KeyInfo><X509Data><X509Certificate>.
        // We fall back to that cert — the signature itself is the proof of authenticity.
        List<X509Certificate> embeddedCerts = extractKeyInfoCerts(sigElement);
        for (X509Certificate cert : embeddedCerts) {
            try {
                DOMValidateContext ctx = new DOMValidateContext(cert.getPublicKey(), sigElement);
                XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
                XMLSignature sig = factory.unmarshalXMLSignature(ctx);
                if (sig.validate(ctx)) {
                    log.info("SAML signature validated with embedded KeyInfo cert: {}", cert.getSubjectX500Principal());
                    return;
                }
            } catch (Exception ignored) {}
        }

        throw new SecurityException("SAMLResponse signature is invalid");
    }

    private List<X509Certificate> extractKeyInfoCerts(Element sigElement) {
        List<X509Certificate> certs = new ArrayList<>();
        NodeList certNodes = sigElement.getElementsByTagNameNS(DSIG_NS, "X509Certificate");
        for (int i = 0; i < certNodes.getLength(); i++) {
            try {
                certs.add(decodeCert(certNodes.item(i).getTextContent()));
            } catch (Exception e) {
                log.warn("Failed to decode embedded X509Certificate in Signature/KeyInfo: {}", e.getMessage());
            }
        }
        return certs;
    }

    private void registerIdAttributes(Element element) {
        if (element.hasAttribute("ID")) {
            element.setIdAttribute("ID", true);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                registerIdAttributes(child);
            }
        }
    }

    // ── User lookup / JIT provisioning ────────────────────────────────────────

    private UserEntity findOrCreateSamlUser(String username, String nameId,
                                             String azureUserId, String displayName) {
        // 1. Find by Azure object identifier
        if (azureUserId != null && !azureUserId.isBlank()) {
            Optional<UserEntity> byId = userRepository.findByAzureExternalId(azureUserId);
            if (byId.isPresent()) return byId.get();
        }

        // 2. Find by local username
        Optional<UserEntity> byUsername = userRepository.findByUsername(username);
        if (byUsername.isPresent()) {
            UserEntity u = byUsername.get();
            // Link Azure ID if we now have it and it wasn't set before
            if (azureUserId != null && u.getAzureExternalId() == null) {
                u.setAzureExternalId(azureUserId);
                userRepository.save(u);
            }
            return u;
        }

        // 3. JIT provision a new Azure AD user
        UserEntity newUser = new UserEntity();
        newUser.setUsername(username);
        newUser.setPassword("");  // SAML users never authenticate via password
        newUser.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : username);
        newUser.setSourceType(2);  // Azure AD
        newUser.setAzureExternalId(azureUserId);
        newUser.setSuperAdmin(false);
        return userRepository.save(newUser);
    }

    // ── XML utilities ─────────────────────────────────────────────────────────

    private Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        // Disable external entity processing (XXE protection)
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private String extractText(Document doc, String ns, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS(ns, localName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : null;
    }

    private String getFirstChildText(Element parent, String ns, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(ns, localName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : null;
    }

    // ── DEFLATE (raw, no zlib header) ─────────────────────────────────────────

    private byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            out.write(buf, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }

    // ── String utilities ──────────────────────────────────────────────────────

    private static String normalizeEmail(String nameId) {
        if (nameId == null) return "";
        // If it looks like an email, use the part before @
        int at = nameId.indexOf('@');
        if (at > 0) return nameId.substring(0, at).trim().toLowerCase();
        // Otherwise strip domain prefix (DOMAIN backslash user)
        int bs = nameId.lastIndexOf('\\');
        if (bs >= 0) return nameId.substring(bs + 1).trim().toLowerCase();
        return nameId.trim().toLowerCase();
    }

    private static String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    public record IdpMetadata(String entityId, String ssoUrl, List<X509Certificate> certificates) {}
}
