package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.TaskProgressDto;
import com.turbotikects.turbotikectsserver.services.TaskProgressService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskProgressService taskProgressService;

    public TaskController(TaskProgressService taskProgressService) {
        this.taskProgressService = taskProgressService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return taskProgressService.subscribe();
    }

    @GetMapping
    public List<TaskProgressDto> getActiveTasks() {
        return taskProgressService.getActiveTasks();
    }
}