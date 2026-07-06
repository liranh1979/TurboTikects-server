package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccelerationRuleGeneratorServiceTest {

    private AccelerationRuleGeneratorService service;

    @BeforeEach
    void setUp() {
        service = new AccelerationRuleGeneratorService(new ObjectMapper());
    }

    @Test
    void statusEqualsConditionGeneratesNegation() {
        String script = service.generate(
                List.of(Map.of("type", "status", "operator", "equals", "value", "open")),
                List.of(Map.of("type", "set_acceleration", "value", 1)));
        assertTrue(script.contains("t.status != 'open'"));
    }

    @Test
    void statusNotEqualsConditionGeneratesEquality() {
        String script = service.generate(
                List.of(Map.of("type", "status", "operator", "not_equals", "value", "closed")),
                List.of(Map.of("type", "set_acceleration", "value", 1)));
        assertTrue(script.contains("t.status == 'closed'"));
    }

    @Test
    void createdBeforeHoursConditionGeneratesCorrectExpression() {
        String script = service.generate(
                List.of(Map.of("type", "created_before_hours", "value", 48)),
                List.of(Map.of("type", "set_acceleration", "value", 1)));
        assertTrue(script.contains("48L"));
        assertTrue(script.contains("toHours()"));
    }

    @Test
    void accelerationLessThanConditionGeneratesNullCoalescing() {
        String script = service.generate(
                List.of(Map.of("type", "acceleration_less_than", "value", 10)),
                List.of(Map.of("type", "set_acceleration", "value", 1)));
        assertTrue(script.contains("(t.acceleration ?: 0) >= 10"));
    }

    @Test
    void hasNoAssigneeConditionGeneratesNullCheck() {
        String script = service.generate(
                List.of(Map.of("type", "has_no_assignee")),
                List.of(Map.of("type", "set_acceleration", "value", 1)));
        assertTrue(script.contains("t.responsibleUserId != null"));
    }

    @Test
    void setAccelerationActionGeneratesAssignment() {
        String script = service.generate(
                List.of(Map.of("type", "has_no_assignee")),
                List.of(Map.of("type", "set_acceleration", "value", 99)));
        assertTrue(script.contains("t.acceleration = 99"));
    }

    @Test
    void setStatusActionGeneratesStringAssignment() {
        String script = service.generate(
                List.of(Map.of("type", "has_no_assignee")),
                List.of(Map.of("type", "set_status", "value", "in_progress")));
        assertTrue(script.contains("t.status = 'in_progress'"));
    }

    @Test
    void assignToUserActionGeneratesAssignment() {
        String script = service.generate(
                List.of(Map.of("type", "has_no_assignee")),
                List.of(Map.of("type", "assign_to_user", "value", 7)));
        assertTrue(script.contains("t.responsibleUserId = 7"));
    }

    @Test
    void assignToGroupActionGeneratesAssignment() {
        String script = service.generate(
                List.of(Map.of("type", "has_no_assignee")),
                List.of(Map.of("type", "assign_to_group", "value", 3)));
        assertTrue(script.contains("t.responsibleGroupId = 3"));
    }

    @Test
    void generatedScriptContainsReturnFalseAndReturnMutated() {
        String script = service.generate(
                List.of(Map.of("type", "has_no_assignee")),
                List.of(Map.of("type", "set_acceleration", "value", 1)));
        assertTrue(script.contains("return false"));
        assertTrue(script.contains("return mutated"));
    }

    @Test
    void generatedScriptCompiles() throws Exception {
        String script = service.generate(
                List.of(
                        Map.of("type", "status", "operator", "equals", "value", "open"),
                        Map.of("type", "created_before_hours", "value", 24),
                        Map.of("type", "acceleration_less_than", "value", 5),
                        Map.of("type", "has_no_assignee")),
                List.of(
                        Map.of("type", "set_acceleration", "value", 10),
                        Map.of("type", "set_status", "value", "in_progress")));
        try (GroovyClassLoader gcl = new GroovyClassLoader()) {
            assertDoesNotThrow(() -> gcl.parseClass(script),
                    "Generated script must compile without errors. Script was:\n" + script);
        }
    }

    @Test
    void singleQuotesInStatusValueAreEscaped() {
        String script = service.generate(
                List.of(Map.of("type", "status", "operator", "equals", "value", "it's_broken")),
                List.of(Map.of("type", "set_acceleration", "value", 1)));
        assertTrue(script.contains("\\'"),
                "Single quote in value must be escaped with \\'. Script was:\n" + script);
    }
}
