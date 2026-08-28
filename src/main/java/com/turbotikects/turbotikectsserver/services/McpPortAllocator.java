package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.repositorys.McpServerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** FEAT-19 — a genuinely new pattern for this codebase (no existing port-allocation/service-
 * registry precedent). Single-JVM deployment, so a synchronized in-process check-then-reserve
 * against the DB's existing ports is sufficient — this app has no clustering/distributed-lock
 * mechanism elsewhere to match anyway. */
@Component
public class McpPortAllocator {

    @Value("${app.mcp.port-range-start:9100}")
    private int rangeStart;

    @Value("${app.mcp.port-range-end:9199}")
    private int rangeEnd;

    private final McpServerRepository repo;

    public McpPortAllocator(McpServerRepository repo) {
        this.repo = repo;
    }

    public synchronized int allocate() {
        for (int port = rangeStart; port <= rangeEnd; port++) {
            if (!repo.existsByPort(port)) return port;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No free port available in the configured MCP range (" + rangeStart + "-" + rangeEnd +
                        "). Increase app.mcp.port-range-end or free up an existing server.");
    }
}
