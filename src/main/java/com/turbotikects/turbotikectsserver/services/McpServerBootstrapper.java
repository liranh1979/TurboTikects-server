package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.entitys.McpServerEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** FEAT-19 — on every boot, redeploys and restarts every enabled MCP server (the in-memory
 * registry starts empty on a fresh JVM regardless of what was previously running). Reuses the
 * existing TaskProgressService/SSE pipeline, so this shows up in the already-existing
 * TaskProgressPanel with zero new frontend work. */
@Slf4j
@Component
public class McpServerBootstrapper implements ApplicationRunner {

    private final McpServerService mcpServerService;
    private final TaskProgressService taskProgressService;

    public McpServerBootstrapper(McpServerService mcpServerService, TaskProgressService taskProgressService) {
        this.mcpServerService = mcpServerService;
        this.taskProgressService = taskProgressService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<McpServerEntity> enabled = mcpServerService.findAllEnabled();
        if (enabled.isEmpty()) return;

        String taskId = taskProgressService.createTask("Starting MCP Servers", enabled.size());
        int done = 0;
        for (McpServerEntity server : enabled) {
            try {
                mcpServerService.deployAndStartOnBoot(server);
                done++;
                taskProgressService.updateProgress(taskId, done, "Started " + server.getName());
            } catch (Exception e) {
                done++;
                log.error("[McpServerBootstrapper] failed to start server {} ({}): {}", server.getId(), server.getName(), e.getMessage(), e);
                taskProgressService.updateProgress(taskId, done, "Failed to start " + server.getName() + ": " + e.getMessage());
            }
        }
        taskProgressService.completeTask(taskId, "Started " + done + "/" + enabled.size() + " MCP servers");
    }
}
