package com.turbotikects.turbotikectsserver.dto.mcp;

import lombok.Data;

/** Where the wrapped target API's credential goes — never carries the secret value itself. Used
 * for the generateScript prompt, which only needs the placement shape: the generated script reads
 * the actual credential from the TARGET_API_CREDENTIAL environment variable at runtime, so the
 * secret never needs to reach the LLM or appear in the reviewable script text. */
@Data
public class McpAuthShapeDto {
    private String type;     // none | api_key | bearer
    private String location; // header | query | body
    private String name;     // header name / query param name / body field name
}
