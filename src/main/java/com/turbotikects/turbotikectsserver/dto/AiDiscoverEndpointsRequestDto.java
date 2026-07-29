package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

/**
 * Step 1 of the external_api wizard's guided AI flow — see TemplateService.aiDiscoverEndpoints'
 * javadoc.
 */
@Data
public class AiDiscoverEndpointsRequestDto {
    private String documentation;
    private String intent;
    /** Null on the first call of a wizard visit — the backend creates an "api_action_builder" AiChatSession and returns its id; every later step in this same visit echoes it back. */
    private Long sessionId;
}
