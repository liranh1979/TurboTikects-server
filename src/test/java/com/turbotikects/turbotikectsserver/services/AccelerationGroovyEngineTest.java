package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.repositorys.AccelerationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AccelerationGroovyEngineTest {

    private AccelerationGroovyEngine engine;

    @BeforeEach
    void setUp() {
        AccelerationRuleRepository mockRepo = Mockito.mock(AccelerationRuleRepository.class);
        Mockito.when(mockRepo.findByIsActiveTrueOrderByExecutionOrderAsc())
               .thenReturn(Collections.emptyList());
        engine = new AccelerationGroovyEngine(mockRepo);
        engine.warmCache();
    }

    private TicketEntity blankTicket() {
        TicketEntity t = new TicketEntity();
        t.setId(1L);
        t.setStatus("open");
        t.setAcceleration(0);
        return t;
    }

    @Test
    void mutatingScriptReturnsTrueAndMutatesEntity() {
        engine.compile(1L, "ticket.acceleration = 99\nreturn true\n");
        TicketEntity ticket = blankTicket();
        AccelerationGroovyEngine.ExecutionResult result = engine.execute(1L, ticket);
        assertTrue(result.mutated());
        assertNull(result.error());
        assertEquals(99, ticket.getAcceleration());
    }

    @Test
    void nonMutatingScriptReturnsFalse() {
        engine.compile(2L, "return false\n");
        TicketEntity ticket = blankTicket();
        AccelerationGroovyEngine.ExecutionResult result = engine.execute(2L, ticket);
        assertFalse(result.mutated());
        assertNull(result.error());
        assertEquals(0, ticket.getAcceleration());
    }

    @Test
    void exceptionInScriptIsCaughtAsErrorStringNotCrash() {
        engine.compile(3L, "throw new RuntimeException('deliberate test error')\nreturn true\n");
        TicketEntity ticket = blankTicket();
        AccelerationGroovyEngine.ExecutionResult result = assertDoesNotThrow(
                () -> engine.execute(3L, ticket),
                "Exception inside script must not propagate out of execute()");
        assertFalse(result.mutated());
        assertNotNull(result.error());
        assertTrue(result.error().contains("deliberate test error"));
    }

    @Test
    void executeWithNoCachedClassReturnsError() {
        TicketEntity ticket = blankTicket();
        AccelerationGroovyEngine.ExecutionResult result = engine.execute(999L, ticket);
        assertFalse(result.mutated());
        assertNotNull(result.error());
        assertTrue(result.error().contains("999"));
    }

    @Test
    void evictRemovesCachedClass() {
        engine.compile(4L, "return true\n");
        assertTrue(engine.isCached(4L));
        engine.evict(4L);
        assertFalse(engine.isCached(4L));
        AccelerationGroovyEngine.ExecutionResult result = engine.execute(4L, blankTicket());
        assertNotNull(result.error());
    }

    @Test
    void scriptReceivesTicketBinding() {
        engine.compile(5L, "return ticket.status == 'open'\n");
        TicketEntity ticket = blankTicket();
        ticket.setStatus("open");
        assertTrue(engine.execute(5L, ticket).mutated());
        ticket.setStatus("closed");
        assertFalse(engine.execute(5L, ticket).mutated());
    }
}
