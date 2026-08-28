package com.turbotikects.turbotikectsserver.dto.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** McpAuthShapeDto plus the plaintext secret — used only on the Step 4 deploy/create request,
 * where it is encrypted immediately (AesEncryptionUtils) before anything is persisted. Never
 * logged, never echoed back in a response. */
@Data
@EqualsAndHashCode(callSuper = true)
public class McpTargetAuthDto extends McpAuthShapeDto {
    @JsonProperty("secret_value")
    private String secretValue;
}
