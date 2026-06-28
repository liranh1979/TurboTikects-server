package com.turbotikects.turbotikectsserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SslInfoDto {
    @JsonProperty("enabled")      private boolean enabled;
    @JsonProperty("cert_type")    private String  certType;
    @JsonProperty("domain")       private String  domain;
    @JsonProperty("https_port")   private int     httpsPort;
    @JsonProperty("cert_subject") private String  certSubject;
    @JsonProperty("cert_issuer")  private String  certIssuer;
    @JsonProperty("cert_expiry")  private String  certExpiry;
    @JsonProperty("installed_at") private String  installedAt;
}
