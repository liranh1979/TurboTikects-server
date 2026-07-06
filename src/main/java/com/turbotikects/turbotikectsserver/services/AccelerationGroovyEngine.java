package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.entitys.AccelerationRuleEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.repositorys.AccelerationRuleRepository;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AccelerationGroovyEngine {

    private final ConcurrentHashMap<Long, Class<? extends Script>> cache = new ConcurrentHashMap<>();
    private final GroovyClassLoader gcl;
    private final AccelerationRuleRepository ruleRepo;

    public AccelerationGroovyEngine(AccelerationRuleRepository ruleRepo) {
        this.ruleRepo = ruleRepo;
        this.gcl = new GroovyClassLoader(Thread.currentThread().getContextClassLoader());
    }

    @PostConstruct
    public void warmCache() {
        for (AccelerationRuleEntity rule : ruleRepo.findByIsActiveTrueOrderByExecutionOrderAsc()) {
            if (rule.getGeneratedScript() != null && !rule.getGeneratedScript().isBlank()) {
                try {
                    compile(rule.getId(), rule.getGeneratedScript());
                } catch (Exception e) {
                    log.warn("Failed to warm-cache acceleration rule {}: {}", rule.getId(), e.getMessage());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void compile(Long ruleId, String scriptText) {
        cache.put(ruleId, (Class<? extends Script>) gcl.parseClass(scriptText));
    }

    public ExecutionResult execute(Long ruleId, TicketEntity ticket) {
        Class<? extends Script> clazz = cache.get(ruleId);
        if (clazz == null) {
            return ExecutionResult.error("Script not compiled for rule " + ruleId);
        }
        try {
            Script instance = clazz.getDeclaredConstructor().newInstance();
            Binding binding = new Binding();
            binding.setVariable("ticket", ticket);
            instance.setBinding(binding);
            Object result = instance.run();
            return ExecutionResult.of(Boolean.TRUE.equals(result), null);
        } catch (Exception e) {
            return ExecutionResult.of(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public void evict(Long ruleId) {
        cache.remove(ruleId);
    }

    public boolean isCached(Long ruleId) {
        return cache.containsKey(ruleId);
    }

    public record ExecutionResult(boolean mutated, String error) {
        static ExecutionResult of(boolean mutated, String error) {
            return new ExecutionResult(mutated, error);
        }
        static ExecutionResult error(String msg) {
            return new ExecutionResult(false, msg);
        }
    }
}
