package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AccelerationSpecificationBuilder {

    private AccelerationSpecificationBuilder() {}

    // ── Public entry points ────────────────────────────────────────────────────

    /** Handles both legacy flat-array JSON and V2 nested group JSON. */
    public static Specification<TicketEntity> fromConditionsJson(String json, ObjectMapper om) {
        try {
            JsonNode node = om.readTree(json);
            if (node.isArray()) {
                List<Map<String, Object>> conditions = om.convertValue(node, new TypeReference<>() {});
                return fromConditions(conditions);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = om.convertValue(node, Map.class);
            return fromGroupMap(root);
        } catch (Exception e) {
            return (r, q, cb) -> cb.conjunction();
        }
    }

    /** Legacy: flat list of conditions, all AND. Kept for backward compatibility. */
    public static Specification<TicketEntity> fromConditions(List<Map<String, Object>> conditions) {
        return (rt, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Map<String, Object> cond : conditions) {
                String type = (String) cond.get("type");
                if (type == null) continue;
                switch (type) {
                    case "status" -> {
                        String op  = (String) cond.getOrDefault("operator", "equals");
                        String val = (String) cond.getOrDefault("value", "");
                        predicates.add("equals".equals(op)
                                ? cb.equal(rt.get("status"), val)
                                : cb.notEqual(rt.get("status"), val));
                    }
                    case "created_before_hours" -> {
                        long hours = toLong(cond.get("value"));
                        predicates.add(cb.lessThan(rt.get("createdAt"), LocalDateTime.now().minusHours(hours)));
                    }
                    case "acceleration_less_than" -> {
                        int val = toInt(cond.get("value"));
                        predicates.add(cb.or(cb.isNull(rt.get("acceleration")),
                                cb.lessThan(rt.get("acceleration"), val)));
                    }
                    case "has_no_assignee" ->
                            predicates.add(cb.isNull(rt.get("responsibleUserId")));
                }
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ── V2 nested group ────────────────────────────────────────────────────────

    private static Specification<TicketEntity> fromGroupMap(Map<String, Object> root) {
        return (rt, query, cb) -> {
            Predicate p = buildPredicate(rt, cb, root);
            return p != null ? p : cb.conjunction();
        };
    }

    /**
     * Recursively builds a JPA predicate for a node.
     * Returns null when the predicate cannot be expressed in SQL (custom fields or OR-with-custom).
     * Callers must handle null: AND groups skip null children; OR groups abort and return null.
     */
    @SuppressWarnings("unchecked")
    private static Predicate buildPredicate(Root<TicketEntity> rt, CriteriaBuilder cb, Map<String, Object> node) {
        if (node.containsKey("combinator")) {
            String combinator = (String) node.getOrDefault("combinator", "AND");
            List<Map<String, Object>> children =
                    (List<Map<String, Object>>) node.getOrDefault("conditions", List.of());
            boolean isAnd = "AND".equalsIgnoreCase(combinator);
            List<Predicate> collected = new ArrayList<>();

            for (Map<String, Object> child : children) {
                Predicate childPred = buildPredicate(rt, cb, child);
                if (childPred == null && !isAnd) {
                    // OR group with un-expressible child → skip SQL pre-filter entirely
                    return null;
                }
                if (childPred != null) collected.add(childPred);
            }

            if (collected.isEmpty()) return null;
            return isAnd
                    ? cb.and(collected.toArray(new Predicate[0]))
                    : cb.or(collected.toArray(new Predicate[0]));
        }

        if (node.containsKey("field")) {
            if (Boolean.TRUE.equals(node.get("isCustom"))) return null; // Groovy handles custom fields

            String field    = (String) node.get("field");
            String fieldType = (String) node.getOrDefault("fieldType", "text");
            String operator  = (String) node.getOrDefault("operator", "equals");
            Object value    = node.get("value");

            try {
                return buildLeafPredicate(rt, cb, field, fieldType, operator, value, node);
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    private static Predicate buildLeafPredicate(Root<TicketEntity> rt, CriteriaBuilder cb,
            String field, String fieldType, String operator, Object value, Map<String, Object> node) {
        return switch (fieldType) {
            case "combobox" -> {
                String val = value != null ? value.toString() : "";
                yield switch (operator) {
                    case "equals"     -> cb.equal(rt.get(field), val);
                    case "not_equals" -> cb.notEqual(rt.get(field), val);
                    default -> null;
                };
            }
            case "text", "email", "phone", "url" -> {
                String val = value != null ? value.toString() : "";
                yield switch (operator) {
                    case "equals"       -> cb.equal(rt.get(field), val);
                    case "not_equals"   -> cb.notEqual(rt.get(field), val);
                    case "contains"     -> cb.like(cb.lower(rt.get(field)), "%" + val.toLowerCase() + "%");
                    case "not_contains" -> cb.notLike(cb.lower(rt.get(field)), "%" + val.toLowerCase() + "%");
                    default -> null;
                };
            }
            case "number" -> {
                int n = toInt(value);
                yield switch (operator) {
                    case "equals"     -> cb.equal(rt.get(field), n);
                    case "not_equals" -> cb.notEqual(rt.get(field), n);
                    case "lt" -> cb.or(cb.isNull(rt.get(field)), cb.lessThan(rt.get(field), n));
                    case "gt" -> cb.greaterThan(rt.get(field), n);
                    default -> null;
                };
            }
            case "date" -> {
                long n = toLong(value);
                String unit = (String) node.getOrDefault("valueUnit", "hours");
                LocalDateTime threshold = switch (unit) {
                    case "minutes" -> LocalDateTime.now().minusMinutes(n);
                    case "days"    -> LocalDateTime.now().minusDays(n);
                    default        -> LocalDateTime.now().minusHours(n);
                };
                yield switch (operator) {
                    case "older_than" -> cb.lessThan(rt.get(field), threshold);
                    case "newer_than" -> cb.greaterThan(rt.get(field), threshold);
                    default -> null;
                };
            }
            default -> null;
        };
    }

    // ── Shared utils ───────────────────────────────────────────────────────────

    private static int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString());
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }
}
