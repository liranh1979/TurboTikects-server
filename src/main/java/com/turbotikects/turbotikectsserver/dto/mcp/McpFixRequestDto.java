package com.turbotikects.turbotikectsserver.dto.mcp;

import lombok.Data;

/** Step 5's "Ask AI to Fix." Only the admin's optional free-text note comes from the frontend —
 * recent logs and the last failed tool call are fetched server-side from McpServerRegistry, so the
 * AI always sees the real, current process state. */
@Data
public class McpFixRequestDto {
    private String adminDescription;
}
