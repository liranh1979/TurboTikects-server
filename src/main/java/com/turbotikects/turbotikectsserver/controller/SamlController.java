package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.services.SamlService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/saml2")
public class SamlController {

    private final SamlService samlService;

    public SamlController(SamlService samlService) {
        this.samlService = samlService;
    }

    /** Step 1: Browser navigates here → builds AuthnRequest → redirects to Azure AD */
    @GetMapping("/login/{configId}")
    public void initiateLogin(@PathVariable Long configId, HttpServletResponse response) throws Exception {
        samlService.initiateSamlLogin(configId, response);
    }

    /** Step 2: Azure AD POSTs the SAMLResponse here after user authenticates */
    @PostMapping(value = "/sso/{configId}", consumes = "application/x-www-form-urlencoded")
    public void processAcs(@PathVariable Long configId,
                           @RequestParam("SAMLResponse") String samlResponse,
                           HttpServletResponse response) throws Exception {
        samlService.processSamlResponse(configId, samlResponse, response);
    }

    /** Returns SP metadata XML for Azure AD Enterprise Application setup */
    @GetMapping(value = "/metadata/{configId}", produces = "application/xml")
    public String getMetadata(@PathVariable Long configId) {
        return samlService.getSpMetadataXml(configId);
    }
}
