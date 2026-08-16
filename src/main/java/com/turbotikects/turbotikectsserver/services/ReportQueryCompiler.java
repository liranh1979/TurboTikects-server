package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Deterministically compiles a report's whitelisted JSON condition tree into a query — the
 * only thing that ever turns an AI (or hand-built) report query proposal into something that
 * actually touches the database. Modeled directly on AccelerationSpecificationBuilder's V2
 * nested AND/OR group shape ({combinator, conditions:[{field, fieldType, operator, value,
 * isCustom}]}) so both this feature and Acceleration Rules speak the same condition-tree
 * dialect. See V2/repoets/feat-05-02-ai-query-agent.html for the full design rationale.
 *
 * Two-tier evaluation, same architecture as Acceleration Rules:
 *  - toSpecification(): best-effort SQL pre-filter over TicketEntity's real JPA columns only.
 *    Any leaf referencing a custom field (isCustom=true, stored in tickets.ticket_data JSON,
 *    not a real column) can't be expressed here and is dropped — an AND drops just that leaf,
 *    an OR containing one drops the whole node (falls back to matching everything at this tier).
 *  - matches(): the authoritative, always-correct Java-side evaluator run over whatever the SQL
 *    tier returned — handles both system and custom fields. toSpecification() is purely a
 *    performance pre-filter; matches() is what actually decides inclusion.
 *
 * SYSTEM_FIELDS is this class's own fixed whitelist of real, JPA-queryable TicketEntity columns
 * (not derived from field_definitions — this codebase's field_definitions rows for
 * entityType='ticket' mix true custom fields with metadata about system fields like status'
 * configurable options, and there's no guaranteed fieldKey-to-JPA-property naming contract to
 * lean on there). Real custom fields (field_definitions.isSystem=false for entityType='ticket',
 * values stored in ticketData) are looked up separately by ReportFieldCatalogService/callers.
 */
public class ReportQueryCompiler {

    private ReportQueryCompiler() {}

    /** fieldKey -> fieldType ("text" | "combobox" | "date" | "number"), display order preserved. */
    public static final Map<String, String> SYSTEM_FIELDS = new LinkedHashMap<>();
    static {
        SYSTEM_FIELDS.put("id", "number");
        SYSTEM_FIELDS.put("title", "text");
        SYSTEM_FIELDS.put("description", "text");
        SYSTEM_FIELDS.put("status", "combobox");
        SYSTEM_FIELDS.put("priority", "combobox");
        SYSTEM_FIELDS.put("sourceType", "combobox");
        SYSTEM_FIELDS.put("responsibleUserId", "number");
        SYSTEM_FIELDS.put("responsibleGroupId", "number");
        SYSTEM_FIELDS.put("requestUserId", "number");
        SYSTEM_FIELDS.put("acceleration", "number");
        SYSTEM_FIELDS.put("createdAt", "date");
        SYSTEM_FIELDS.put("updatedAt", "date");
    }

    public static boolean isSystemField(String field) {
        return SYSTEM_FIELDS.containsKey(field);
    }

    // ── SQL pre-filter ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public static Specification<TicketEntity> toSpecification(Map<String, Object> conditionsNode) {
        if (conditionsNode == null || conditionsNode.isEmpty()) {
            return (rt, q, cb) -> cb.conjunction();
        }
        return (rt, query, cb) -> {
            Predicate p = buildPredicate(rt, cb, conditionsNode);
            return p != null ? p : cb.conjunction();
        };
    }

    @SuppressWarnings("unchecked")
    private static Predicate buildPredicate(Root<TicketEntity> rt, CriteriaBuilder cb, Map<String, Object> node) {
        if (node.containsKey("combinator")) {
            String combinator = (String) node.getOrDefault("combinator", "AND");
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.getOrDefault("conditions", List.of());
            boolean isAnd = "AND".equalsIgnoreCase(combinator);
            List<Predicate> collected = new ArrayList<>();

            for (Map<String, Object> child : children) {
                Predicate childPred = buildPredicate(rt, cb, child);
                if (childPred == null && !isAnd) return null; // OR with an un-expressible child — skip SQL pre-filter entirely
                if (childPred != null) collected.add(childPred);
            }
            if (collected.isEmpty()) return null;
            return isAnd ? cb.and(collected.toArray(new Predicate[0])) : cb.or(collected.toArray(new Predicate[0]));
        }

        if (node.containsKey("field")) {
            if (Boolean.TRUE.equals(node.get("isCustom"))) return null; // handled only by matches()
            String field = (String) node.get("field");
            if (!isSystemField(field)) return null;
            String fieldType = SYSTEM_FIELDS.get(field);
            String operator = (String) node.getOrDefault("operator", "equals");
            Object value = node.get("value");
            try {
                return buildLeafPredicate(rt, cb, field, fieldType, operator, value);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static Predicate buildLeafPredicate(Root<TicketEntity> rt, CriteriaBuilder cb,
            String field, String fieldType, String operator, Object value) {
        return switch (fieldType) {
            case "combobox" -> {
                String val = value != null ? value.toString() : "";
                yield switch (operator) {
                    case "equals" -> cb.equal(rt.get(field), val);
                    case "not_equals" -> cb.notEqual(rt.get(field), val);
                    default -> null;
                };
            }
            case "text" -> {
                String val = value != null ? value.toString() : "";
                yield switch (operator) {
                    case "equals" -> cb.equal(rt.get(field), val);
                    case "not_equals" -> cb.notEqual(rt.get(field), val);
                    case "contains" -> cb.like(cb.lower(rt.get(field)), "%" + val.toLowerCase() + "%");
                    case "not_contains" -> cb.notLike(cb.lower(rt.get(field)), "%" + val.toLowerCase() + "%");
                    default -> null;
                };
            }
            case "number" -> {
                int n = toInt(value);
                yield switch (operator) {
                    case "equals" -> cb.equal(rt.get(field), n);
                    case "not_equals" -> cb.notEqual(rt.get(field), n);
                    case "lt" -> cb.lessThan(rt.get(field), n);
                    case "gt" -> cb.greaterThan(rt.get(field), n);
                    default -> null;
                };
            }
            case "date" -> {
                LocalDateTime[] range = toDateRange(value);
                if (range == null) yield null;
                yield switch (operator) {
                    case "older_than" -> cb.lessThan(rt.get(field), range[0]);
                    case "newer_than" -> cb.greaterThan(rt.get(field), range[0]);
                    case "is_between" -> cb.between(rt.get(field), range[0], range[1]);
                    default -> null;
                };
            }
            default -> null;
        };
    }

    // ── Java-side authoritative evaluator ──────────────────────────────────────

    /**
     * @param ticketDataLookup resolves a custom fieldKey to its value from the ticket's
     *                         ticketData JSON map (null-safe — callers pass ticket.getTicketData()).
     */
    @SuppressWarnings("unchecked")
    public static boolean matches(Map<String, Object> conditionsNode, Object systemFieldSource,
                                   Map<String, Object> ticketDataLookup) {
        if (conditionsNode == null || conditionsNode.isEmpty()) return true;
        return evaluate(conditionsNode, systemFieldSource, ticketDataLookup);
    }

    @SuppressWarnings("unchecked")
    private static boolean evaluate(Map<String, Object> node, Object src, Map<String, Object> ticketData) {
        if (node.containsKey("combinator")) {
            String combinator = (String) node.getOrDefault("combinator", "AND");
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.getOrDefault("conditions", List.of());
            boolean isAnd = "AND".equalsIgnoreCase(combinator);
            if (children.isEmpty()) return true;
            for (Map<String, Object> child : children) {
                boolean r = evaluate(child, src, ticketData);
                if (isAnd && !r) return false;
                if (!isAnd && r) return true;
            }
            return isAnd;
        }
        if (node.containsKey("field")) {
            String field = (String) node.get("field");
            boolean isCustom = Boolean.TRUE.equals(node.get("isCustom"));
            String operator = (String) node.getOrDefault("operator", "equals");
            Object expected = node.get("value");
            Object actual = isCustom ? (ticketData == null ? null : ticketData.get(field)) : readSystemField(src, field);
            return evaluateLeaf(actual, operator, expected);
        }
        return true;
    }

    private static boolean evaluateLeaf(Object actual, String operator, Object expected) {
        String a = actual == null ? "" : String.valueOf(actual);
        String e = expected == null ? "" : String.valueOf(expected);
        return switch (operator) {
            case "equals" -> a.equalsIgnoreCase(e);
            case "not_equals" -> !a.equalsIgnoreCase(e);
            case "contains" -> a.toLowerCase().contains(e.toLowerCase());
            case "not_contains" -> !a.toLowerCase().contains(e.toLowerCase());
            case "gt" -> safeDouble(actual) > safeDouble(expected);
            case "lt" -> safeDouble(actual) < safeDouble(expected);
            default -> true; // unknown operator — fail open at the Java tier, SQL tier already narrowed
        };
    }

    /** Public accessor for callers rendering a row (e.g. preview/export) that need a system field's
     * real value, not just a boolean match — kept as one authoritative switch statement rather than
     * duplicating this mapping elsewhere. */
    public static Object getSystemFieldValue(TicketEntity t, String field) {
        return readSystemField(t, field);
    }

    private static Object readSystemField(Object src, String field) {
        if (!(src instanceof TicketEntity t)) return null;
        return switch (field) {
            case "id" -> t.getId();
            case "title" -> t.getTitle();
            case "description" -> t.getDescription();
            case "status" -> t.getStatus();
            case "priority" -> t.getPriority();
            case "sourceType" -> t.getSourceType();
            case "responsibleUserId" -> t.getResponsibleUserId();
            case "responsibleGroupId" -> t.getResponsibleGroupId();
            case "requestUserId" -> t.getRequestUserId();
            case "acceleration" -> t.getAcceleration();
            case "createdAt" -> t.getCreatedAt();
            case "updatedAt" -> t.getUpdatedAt();
            default -> null;
        };
    }

    private static int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); } catch (Exception e) { return 0; }
    }

    private static double safeDouble(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); } catch (Exception e) { return 0; }
    }

    /** Accepts either a single ISO datetime/relative-hours-ago style value or a [from,to] pair. */
    private static LocalDateTime[] toDateRange(Object value) {
        try {
            if (value instanceof List<?> list && list.size() == 2) {
                return new LocalDateTime[]{ parseFlexibleDate(list.get(0)), parseFlexibleDate(list.get(1)) };
            }
            LocalDateTime single = parseFlexibleDate(value);
            return new LocalDateTime[]{ single, single };
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime parseFlexibleDate(Object value) {
        String s = String.valueOf(value);
        if (s.length() == 10) return LocalDateTime.parse(s + "T00:00:00");
        return LocalDateTime.parse(s);
    }
}
