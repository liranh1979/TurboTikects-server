package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.Map;

/**
 * Step 2 of the external_api wizard's guided AI flow — see TemplateService.aiDraftCallSkeleton's
 * javadoc.
 */
@Data
public class AiDraftCallSkeletonRequestDto {
    private Long sessionId;
    private String intent;
    /** Echoed verbatim from Step 1's aiDiscoverEndpoints response — {id, method, title, summary, recommended, docExcerpt}. */
    private Map<String, Object> selectedEndpoint;
}
