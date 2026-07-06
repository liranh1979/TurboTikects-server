package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AccelerationRuleGeneratorService {

    private final ObjectMapper objectMapper;

    public AccelerationRuleGeneratorService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Public entry points ────────────────────────────────────────────────────

    /** Called by the controller's /generate endpoint. Handles both array (legacy) and object (V2) formats. */
    public String generate(String triggerConditionsJson, String actionsJson) {
        try {
            JsonNode condNode = objectMapper.readTree(triggerConditionsJson);
            List<Map<String, Object>> actions = objectMapper.readValue(actionsJson, new TypeReference<>() {});
            if (condNode.isArray()) {
                List<Map<String, Object>> conditions = objectMapper.convertValue(condNode, new TypeReference<>() {});
                return generateLegacy(conditions, actions);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> root = objectMapper.convertValue(condNode, Map.class);
                return generateWithGroup(root, actions);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON in conditions or actions: " + e.getMessage(), e);
        }
    }

    /** Called by unit tests — routes by key presence. */
    public String generate(List<Map<String, Object>> conditions, List<Map<String, Object>> actions) {
        if (conditions.isEmpty() || conditions.get(0).containsKey("type")) {
            return generateLegacy(conditions, actions);
        }
        Map<String, Object> root = Map.of("combinator", "AND", "conditions", conditions);
        return generateWithGroup(root, actions);
    }

    // ── Legacy generator (V71 flat-array format) ───────────────────────────────

    private String generateLegacy(List<Map<String, Object>> conditions, List<Map<String, Object>> actions) {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb);

        if (!conditions.isEmpty()) {
            sb.append("// --- Conditions (ALL must be true) ---\n");
            for (Map<String, Object> cond : conditions) {
                appendLegacyCondition(sb, cond);
            }
            sb.append("\n");
        }

        appendActionsBlock(sb, actions);
        sb.append("return mutated\n");
        return sb.toString();
    }

    private void appendLegacyCondition(StringBuilder sb, Map<String, Object> cond) {
        String type = (String) cond.get("type");
        if (type == null) return;
        switch (type) {
            case "status" -> {
                String op  = (String) cond.getOrDefault("operator", "equals");
                String val = escapeGroovySingleQuote((String) cond.getOrDefault("value", ""));
                if ("equals".equals(op)) {
                    sb.append("if (t.status != '").append(val).append("') { return false }\n");
                } else {
                    sb.append("if (t.status == '").append(val).append("') { return false }\n");
                }
            }
            case "created_before_hours" -> {
                long hours = toLong(cond.get("value"));
                sb.append("if (Duration.between(t.createdAt, LocalDateTime.now()).toHours() <= ")
                  .append(hours).append("L) { return false }\n");
            }
            case "acceleration_less_than" -> {
                int val = toInt(cond.get("value"));
                sb.append("if ((t.acceleration ?: 0) >= ").append(val).append(") { return false }\n");
            }
            case "has_no_assignee" ->
                sb.append("if (t.responsibleUserId != null) { return false }\n");
        }
    }

    // ── V2 recursive generator ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String generateWithGroup(Map<String, Object> root, List<Map<String, Object>> actions) {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb);

        List<?> rootConditions = (List<?>) root.get("conditions");
        if (rootConditions != null && !rootConditions.isEmpty()) {
            sb.append("// --- Conditions ---\n");
            int[] counter = {0};
            String topVar = emitGroup(sb, root, counter);
            sb.append("if (!").append(topVar).append(") { return false }\n\n");
        }

        appendActionsBlock(sb, actions);
        sb.append("return mutated\n");
        return sb.toString();
    }

    /**
     * Recursively emits a condition group. Returns the name of the boolean variable that holds the result.
     * AND group: _gN = true; each child sets it false on mismatch.
     * OR  group: _gN = false; each child sets it true on match.
     */
    @SuppressWarnings("unchecked")
    private String emitGroup(StringBuilder sb, Map<String, Object> node, int[] counter) {
        String varName = "_g" + counter[0]++;
        String combinator = (String) node.getOrDefault("combinator", "AND");
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.getOrDefault("conditions", List.of());
        boolean isAnd = "AND".equalsIgnoreCase(combinator);

        sb.append("boolean ").append(varName).append(" = ").append(isAnd ? "true" : "false").append("\n");

        for (Map<String, Object> child : children) {
            if (child.containsKey("combinator")) {
                String subVar = emitGroup(sb, child, counter);
                if (isAnd) {
                    sb.append("if (!").append(subVar).append(") { ").append(varName).append(" = false }\n");
                } else {
                    sb.append("if (").append(subVar).append(") { ").append(varName).append(" = true }\n");
                }
            } else {
                String expr = buildPositiveExpression(child);
                if (expr != null) {
                    if (isAnd) {
                        sb.append("if (").append(varName).append(" && !(").append(expr)
                          .append(")) { ").append(varName).append(" = false }\n");
                    } else {
                        sb.append("if (").append(expr).append(") { ").append(varName).append(" = true }\n");
                    }
                }
            }
        }
        return varName;
    }

    private String buildPositiveExpression(Map<String, Object> leaf) {
        boolean isCustom = Boolean.TRUE.equals(leaf.get("isCustom"));
        String field    = (String) leaf.get("field");
        String fieldType = (String) leaf.getOrDefault("fieldType", "text");
        String operator  = (String) leaf.getOrDefault("operator", "equals");
        if (field == null) return null;
        return isCustom
                ? buildCustomExpr(field, fieldType, operator, leaf)
                : buildSystemExpr(field, fieldType, operator, leaf);
    }

    private String buildSystemExpr(String field, String fieldType, String operator, Map<String, Object> leaf) {
        String escaped = escapeGroovySingleQuote(strVal(leaf.get("value")));
        return switch (fieldType) {
            case "combobox" -> switch (operator) {
                case "equals"     -> "t." + field + " == '" + escaped + "'";
                case "not_equals" -> "t." + field + " != '" + escaped + "'";
                default -> null;
            };
            case "text", "email", "phone", "url" -> switch (operator) {
                case "equals"       -> "(t." + field + " ?: '') == '" + escaped + "'";
                case "not_equals"   -> "(t." + field + " ?: '') != '" + escaped + "'";
                case "contains"     -> "(t." + field + "?.contains('" + escaped + "') ?: false)";
                case "not_contains" -> "!(t." + field + "?.contains('" + escaped + "') ?: false)";
                default -> null;
            };
            case "number" -> {
                long n = toLong(leaf.get("value"));
                yield switch (operator) {
                    case "equals"     -> "(t." + field + " ?: 0) == " + n;
                    case "not_equals" -> "(t." + field + " ?: 0) != " + n;
                    case "lt"         -> "(t." + field + " ?: 0) < " + n;
                    case "gt"         -> "(t." + field + " ?: 0) > " + n;
                    default -> null;
                };
            }
            case "date" -> {
                long n = toLong(leaf.get("value"));
                String method = dateMethod((String) leaf.get("valueUnit"));
                yield switch (operator) {
                    case "older_than" -> "Duration.between(t." + field + ", LocalDateTime.now())." + method + "() >= " + n + "L";
                    case "newer_than" -> "Duration.between(t." + field + ", LocalDateTime.now())." + method + "() < " + n + "L";
                    default -> null;
                };
            }
            default -> null;
        };
    }

    private String buildCustomExpr(String field, String fieldType, String operator, Map<String, Object> leaf) {
        String safeKey = escapeGroovySingleQuote(field);
        String g = "t.ticketData?.get('" + safeKey + "')";
        String escaped = escapeGroovySingleQuote(strVal(leaf.get("value")));
        return switch (fieldType) {
            case "text", "email", "phone", "url" -> switch (operator) {
                case "equals"       -> "(" + g + "?.toString() ?: '') == '" + escaped + "'";
                case "not_equals"   -> "(" + g + "?.toString() ?: '') != '" + escaped + "'";
                case "contains"     -> "(" + g + "?.toString()?.contains('" + escaped + "') ?: false)";
                case "not_contains" -> "!(" + g + "?.toString()?.contains('" + escaped + "') ?: false)";
                default -> null;
            };
            case "number" -> {
                long n = toLong(leaf.get("value"));
                yield switch (operator) {
                    case "equals"     -> "(((" + g + ") as Number)?.intValue() ?: 0) == " + n;
                    case "not_equals" -> "(((" + g + ") as Number)?.intValue() ?: 0) != " + n;
                    case "lt"         -> "(((" + g + ") as Number)?.intValue() ?: 0) < " + n;
                    case "gt"         -> "(((" + g + ") as Number)?.intValue() ?: 0) > " + n;
                    default -> null;
                };
            }
            case "combobox" -> switch (operator) {
                case "equals"     -> "(" + g + "?.toString() ?: '') == '" + escaped + "'";
                case "not_equals" -> "(" + g + "?.toString() ?: '') != '" + escaped + "'";
                default -> null;
            };
            case "multi-select", "multi_select" -> switch (operator) {
                case "contains"     -> "(" + g + " instanceof List ? ((" + g + ") as List).contains('" + escaped + "') : false)";
                case "not_contains" -> "!(" + g + " instanceof List ? ((" + g + ") as List).contains('" + escaped + "') : false)";
                default -> null;
            };
            case "checkbox" -> switch (operator) {
                case "is_true"  -> "Boolean.TRUE.equals(" + g + " instanceof Boolean ? " + g + " : false)";
                case "is_false" -> "!Boolean.TRUE.equals(" + g + " instanceof Boolean ? " + g + " : false)";
                default -> null;
            };
            case "date" -> {
                long n = toLong(leaf.get("value"));
                String method = dateMethod((String) leaf.get("valueUnit"));
                yield switch (operator) {
                    case "older_than" ->
                        "Duration.between(java.time.LocalDate.parse((" + g + ")?.toString() ?: '1970-01-01').atStartOfDay(), LocalDateTime.now())." + method + "() >= " + n + "L";
                    case "newer_than" ->
                        "Duration.between(java.time.LocalDate.parse((" + g + ")?.toString() ?: '1970-01-01').atStartOfDay(), LocalDateTime.now())." + method + "() < " + n + "L";
                    default -> null;
                };
            }
            default -> null;
        };
    }

    // ── Shared helpers ─────────────────────────────────────────────────────────

    private void appendHeader(StringBuilder sb) {
        sb.append("// Auto-generated by AccelerationRuleGeneratorService — do not edit manually\n");
        sb.append("import java.time.LocalDateTime\n");
        sb.append("import java.time.Duration\n\n");
        sb.append("def t = ticket\n");
        sb.append("boolean mutated = false\n\n");
    }

    private void appendActionsBlock(StringBuilder sb, List<Map<String, Object>> actions) {
        if (actions.isEmpty()) return;
        sb.append("// --- Actions ---\n");
        for (Map<String, Object> action : actions) {
            appendAction(sb, action);
        }
        sb.append("\n");
    }

    private void appendAction(StringBuilder sb, Map<String, Object> action) {
        String type = (String) action.get("type");
        if (type == null) return;
        switch (type) {
            case "set_acceleration" -> {
                sb.append("t.acceleration = ").append(toInt(action.get("value"))).append("\n");
                sb.append("mutated = true\n");
            }
            case "set_status" -> {
                sb.append("t.status = '").append(escapeGroovySingleQuote(strVal(action.get("value")))).append("'\n");
                sb.append("mutated = true\n");
            }
            case "assign_to_user" -> {
                sb.append("t.responsibleUserId = ").append(toInt(action.get("value"))).append("\n");
                sb.append("mutated = true\n");
            }
            case "assign_to_group" -> {
                sb.append("t.responsibleGroupId = ").append(toInt(action.get("value"))).append("\n");
                sb.append("mutated = true\n");
            }
        }
    }

    private String dateMethod(String unit) {
        if (unit == null) return "toHours";
        return switch (unit) {
            case "minutes" -> "toMinutes";
            case "days"    -> "toDays";
            default        -> "toHours";
        };
    }

    private String escapeGroovySingleQuote(String value) {
        if (value == null) return "";
        return value.replace("'", "\\'");
    }

    private String strVal(Object v) {
        return v == null ? "" : v.toString();
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString());
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }
}
