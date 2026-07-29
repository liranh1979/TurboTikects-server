package com.turbotikects.turbotikectsserver.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiRefineCallInputDto {
    // The call's real, already-existing id — must be echoed back unchanged by the AI, never
    // regenerated (unlike a from-scratch draft, this is refining ONE piece of an already-saved
    // call, so nothing else about it may be reassigned a new identity).
    private String id;
    // call.name (external_api) or toolName (mcp_tool) — context only, for prompt readability.
    private String name;
    // Current {name,jsonPath} / {name,resultPath} entries — given as context so the AI can adjust
    // rather than blindly discard the admin's own naming choices.
    private List<Map<String, Object>> existingResponseCaptures;
    // The REAL captured response body/object from a live "Test this call now" run — this is the
    // ground truth the AI must derive paths against, not a guess from prose documentation.
    private String rawResponse;
}
