package com.srm.creditengine.reporting.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders the production-owned statement SQL template and its optional filters. */
final class SettlementStatementSql {
    static final String RESOURCE = "sql/settlement-statement.sql";
    private static final Pattern CONDITIONAL_LINE =
            Pattern.compile("^\\s*/\\*\\?([A-Za-z][A-Za-z0-9]*)\\*/\\s*(.+)$");
    private static final Pattern NAMED_PARAMETER =
            Pattern.compile("(?<!:):[A-Za-z][A-Za-z0-9]*");
    private static final Map<String, Function<SettlementStatementService.Filter, Object>> FILTER_VALUES =
            filterValues();

    private final String template;

    private SettlementStatementSql(String template) {
        this.template = template;
        validateTemplate();
    }

    static SettlementStatementSql fromClasspath() {
        ClassLoader loader = SettlementStatementSql.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing statement SQL resource: " + RESOURCE);
            }
            return new SettlementStatementSql(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read statement SQL resource: " + RESOURCE, exception);
        }
    }

    static SettlementStatementSql fromTemplate(String template) {
        return new SettlementStatementSql(template);
    }

    Query render(SettlementStatementService.Filter filter, int limit, long offset) {
        var arguments = new ArrayList<>();
        var sql = new StringBuilder();
        for (String line : template.lines().toList()) {
            Matcher conditional = CONDITIONAL_LINE.matcher(line);
            if (!conditional.matches()) {
                sql.append(line).append('\n');
                continue;
            }
            String key = conditional.group(1);
            Object value = FILTER_VALUES.get(key).apply(filter);
            if (value != null) {
                String namedParameter = ":" + key;
                String clause = conditional.group(2);
                requireOccurrenceCount(clause, namedParameter, 1);
                sql.append(clause.replace(namedParameter, "?")).append('\n');
                arguments.add(value);
            }
        }
        String rendered = replaceRequired(sql.toString(), ":limit", "?");
        rendered = replaceRequired(rendered, ":offset", "?");
        Matcher dangling = NAMED_PARAMETER.matcher(rendered);
        if (dangling.find()) {
            throw new IllegalStateException("Unresolved statement SQL parameter: " + dangling.group());
        }
        arguments.add(limit);
        arguments.add(offset);
        return new Query(rendered.strip(), List.copyOf(arguments));
    }

    private void validateTemplate() {
        var found = new ArrayList<String>();
        for (String line : template.lines().toList()) {
            Matcher conditional = CONDITIONAL_LINE.matcher(line);
            if (conditional.matches()) {
                if (found.contains(conditional.group(1))) {
                    throw new IllegalStateException(
                            "Duplicate statement SQL filter marker: " + conditional.group(1));
                }
                found.add(conditional.group(1));
            }
        }
        List<String> expected = List.copyOf(FILTER_VALUES.keySet());
        if (!found.equals(expected)) {
            throw new IllegalStateException(
                    "Statement SQL filter markers must be exactly " + expected
                            + " but were " + found);
        }
        requireOccurrenceCount(template, ":limit", 1);
        requireOccurrenceCount(template, ":offset", 1);
    }

    private static Map<String, Function<SettlementStatementService.Filter, Object>> filterValues() {
        var values = new LinkedHashMap<String, Function<SettlementStatementService.Filter, Object>>();
        values.put("from", filter -> timestamp(filter.from()));
        values.put("to", filter -> timestamp(filter.to()));
        values.put("assignorId", SettlementStatementService.Filter::assignorId);
        values.put("assetCurrency", SettlementStatementService.Filter::assetCurrency);
        values.put("settlementCurrency", SettlementStatementService.Filter::settlementCurrency);
        values.put("productType", SettlementStatementService.Filter::productType);
        return Collections.unmodifiableMap(values);
    }

    private static Timestamp timestamp(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static String replaceRequired(String value, String target, String replacement) {
        requireOccurrenceCount(value, target, 1);
        return value.replace(target, replacement);
    }

    private static void requireOccurrenceCount(String value, String target, int expected) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        if (count != expected) {
            throw new IllegalStateException(
                    "Statement SQL must contain " + target + " exactly " + expected + " time(s)");
        }
    }

    record Query(String sql, List<Object> arguments) {}
}
