package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.TaskProgressDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class TaskProgressService {

    private final Map<String, TaskProgressDto> activeTasks = new ConcurrentHashMap<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public String createTask(String name, int total) {
        String id = UUID.randomUUID().toString();
        TaskProgressDto task = new TaskProgressDto();
        task.setId(id);
        task.setName(name);
        task.setTotal(total);
        task.setCurrent(0);
        task.setPercentage(0);
        task.setStatus("running");
        task.setMessage("Starting…");
        activeTasks.put(id, task);
        broadcast(task);
        return id;
    }

    public void updateProgress(String taskId, int current, String message) {
        TaskProgressDto task = activeTasks.get(taskId);
        if (task == null) return;
        task.setCurrent(current);
        task.setPercentage(task.getTotal() > 0 ? (int) ((double) current / task.getTotal() * 100) : 0);
        task.setMessage(message);
        broadcast(task);
    }

    public void completeTask(String taskId, String message) {
        TaskProgressDto task = activeTasks.get(taskId);
        if (task == null) return;
        task.setCurrent(task.getTotal());
        task.setPercentage(100);
        task.setStatus("completed");
        task.setMessage(message);
        broadcast(task);
        // Keep completed tasks briefly visible, then remove
        new Timer().schedule(new TimerTask() {
            public void run() { activeTasks.remove(taskId); broadcast(task); }
        }, 8000);
    }

    public void failTask(String taskId, String message) {
        TaskProgressDto task = activeTasks.get(taskId);
        if (task == null) return;
        task.setStatus("failed");
        task.setMessage(message);
        broadcast(task);
    }

    public List<TaskProgressDto> getActiveTasks() {
        return new ArrayList<>(activeTasks.values());
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        // Send all currently active tasks on connect
        activeTasks.values().forEach(t -> sendToEmitter(emitter, t));
        return emitter;
    }

    private void broadcast(TaskProgressDto task) {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            if (!sendToEmitter(emitter, task)) dead.add(emitter);
        }
        emitters.removeAll(dead);
    }

    private boolean sendToEmitter(SseEmitter emitter, TaskProgressDto task) {
        try {
            emitter.send(SseEmitter.event().name("task-progress").data(mapper.writeValueAsString(task)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}