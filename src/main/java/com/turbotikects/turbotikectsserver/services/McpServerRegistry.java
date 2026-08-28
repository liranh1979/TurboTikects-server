package com.turbotikects.turbotikectsserver.services;

import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * FEAT-19 — in-memory observed state for locally-run MCP servers, mirroring
 * {@link TaskProgressService}'s in-memory bookkeeping style. Never persisted: DB (mcp_servers) is
 * desired configuration, this registry is reality, and is rebuilt from the DB on every boot by
 * McpServerBootstrapper.
 */
@Service
public class McpServerRegistry {

    public enum Status { STOPPED, STARTING, RUNNING, ERROR }

    private static final int MAX_LOG_LINES = 500;

    public static class RunningMcpServer {
        public volatile Status status = Status.STOPPED;
        public volatile Process process;
        public volatile Integer toolCount;
        public volatile String lastError;
        /** Set by testTool on a failed call — name/args/error, consumed (and never typed by the
         * admin) by the "Ask AI to Fix" flow. */
        public volatile String lastFailedToolCallSummary;
        public final Deque<String> logBuffer = new ConcurrentLinkedDeque<>();
    }

    private final Map<Long, RunningMcpServer> servers = new ConcurrentHashMap<>();

    public RunningMcpServer getOrCreate(Long id) {
        return servers.computeIfAbsent(id, k -> new RunningMcpServer());
    }

    public RunningMcpServer get(Long id) {
        return servers.get(id);
    }

    public void remove(Long id) {
        RunningMcpServer s = servers.remove(id);
        if (s != null && s.process != null && s.process.isAlive()) {
            s.process.destroyForcibly();
        }
    }

    public void appendLog(Long id, String line) {
        RunningMcpServer s = getOrCreate(id);
        s.logBuffer.addLast(line);
        while (s.logBuffer.size() > MAX_LOG_LINES) {
            s.logBuffer.pollFirst();
        }
    }

    public String recentLogs(Long id, int maxLines) {
        RunningMcpServer s = servers.get(id);
        if (s == null || s.logBuffer.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int skip = Math.max(0, s.logBuffer.size() - maxLines);
        int i = 0;
        for (String line : s.logBuffer) {
            if (i++ < skip) continue;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** Every currently tracked process — used only to stop everything on shutdown. */
    public Map<Long, RunningMcpServer> all() {
        return servers;
    }
}
