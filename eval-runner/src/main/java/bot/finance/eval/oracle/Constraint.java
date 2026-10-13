package bot.finance.eval.oracle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** A field constraint from a case: {@code equals}, {@code oneOf}, {@code noneOf}, {@code present}, {@code absent}. */
public final class Constraint {

    private static final Set<String> KEYS = Set.of("equals", "oneOf", "noneOf", "present", "absent", "ignored");

    private Constraint() {}

    /** Every constraint inside {@code node}, each with the dotted path that leads to it. */
    public static List<PathConstraint> flatten(JsonNode node) {
        List<PathConstraint> constraints = new ArrayList<>();
        flatten("", node, constraints);
        return constraints;
    }

    private static void flatten(String prefix, JsonNode node, List<PathConstraint> into) {
        if (node.isObject() && node.propertyNames().stream().anyMatch(KEYS::contains)) {
            into.add(new PathConstraint(prefix, node));
            return;
        }
        for (String name : node.propertyNames()) {
            flatten(prefix.isEmpty() ? name : prefix + "." + name, node.get(name), into);
        }
    }

    public static boolean holds(JsonNode constraint, String actual) {
        if (constraint.path("ignored").asBoolean(false)) {
            return true;
        }
        if (constraint.has("absent")) {
            return constraint.get("absent").asBoolean() == (actual == null);
        }
        if (constraint.has("present")) {
            return constraint.get("present").asBoolean() == (actual != null && !actual.isBlank());
        }
        if (constraint.has("equals")) {
            return constraint.get("equals").asString().equals(actual);
        }
        if (constraint.has("oneOf")) {
            return values(constraint.get("oneOf")).contains(actual);
        }
        if (constraint.has("noneOf")) {
            return !values(constraint.get("noneOf")).contains(actual);
        }
        throw new IllegalArgumentException("unknown constraint " + constraint);
    }

    /** {@code node} with every date token among its string values resolved. */
    public static JsonNode resolved(JsonNode node, LocalDate runDate) {
        if (node.isString()) {
            return JsonNodeFactory.instance.stringNode(DateTokens.resolve(node.asString(), runDate));
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            node.forEach(element -> copy.add(resolved(element, runDate)));
            return copy;
        }
        if (node.isObject()) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            for (String name : node.propertyNames()) {
                copy.set(name, resolved(node.get(name), runDate));
            }
            return copy;
        }
        return node;
    }

    /** Whether {@code node} names a date token anywhere, which makes its case sensitive to a UTC rollover. */
    public static boolean namesDateToken(JsonNode node) {
        if (node.isString()) {
            return DateTokens.isToken(node.asString());
        }
        for (JsonNode child : node) {
            if (namesDateToken(child)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> values(JsonNode list) {
        List<String> values = new ArrayList<>();
        list.forEach(value -> values.add(value.asString()));
        return values;
    }

    public record PathConstraint(String path, JsonNode constraint) {}
}
