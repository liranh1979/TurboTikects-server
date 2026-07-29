package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;

/**
 * "Verify Captures" — Response Mapping step, external_api only. Reuses AiRefineCallInputDto's
 * exact shape (id, name, existingResponseCaptures, rawResponse) since it's already exactly what's
 * needed here too: the call id, its current (AI-proposed or admin-edited) responseCaptures to
 * evaluate, and the real response already captured by an earlier "Test this call now" run. See
 * ExternalApiActionExecutor.evaluateResponseCaptures' javadoc.
 */
@Data
public class EvaluateResponseCapturesRequestDto {
    private List<AiRefineCallInputDto> calls;
}
