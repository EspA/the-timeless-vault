package com.thetimelessvault.brickowl;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.common.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class BrickOwlCatalog {

    private BrickOwlCatalog() {
    }

    static String resolveSetBoid(JsonNode lookup, String setNumber) {
        List<Candidate> candidates = candidates(lookup);
        if (candidates.isEmpty()) {
            throw ApiException.badRequest("Brick Owl has no catalog match for set " + label(setNumber));
        }
        if (candidates.size() == 1) {
            return candidates.getFirst().boid();
        }
        String wanted = normalizeSet(setNumber);
        List<Candidate> exact = candidates.stream()
                .filter(candidate -> candidate.setNumbers().stream().anyMatch(id -> normalizeSet(id).equals(wanted)))
                .toList();
        if (exact.size() == 1) {
            return exact.getFirst().boid();
        }
        List<Candidate> remaining = exact.isEmpty() ? candidates : exact;
        throw ApiException.badRequest(
                "Brick Owl has multiple catalog matches for set " + label(setNumber) + ": " + names(remaining)
        );
    }

    static List<Candidate> candidates(JsonNode lookup) {
        List<Candidate> found = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collect(lookup, found, seen);
        return found;
    }

    private static void collect(JsonNode node, List<Candidate> found, Set<String> seen) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collect(child, found, seen));
            return;
        }
        if (!node.isObject()) {
            add(text(node), List.of(), null, found, seen);
            return;
        }
        JsonNode boids = node.get("boids");
        if (boids != null && !boids.isNull()) {
            collect(boids, found, seen);
        }
        String boid = firstText(node, "boid", "id");
        if (boid != null && looksLikeBoid(boid, node)) {
            add(boid, setNumbers(node), firstText(node, "name", "title"), found, seen);
        }
        node.fields().forEachRemaining(entry -> {
            if ("boids".equals(entry.getKey()) || "boid".equals(entry.getKey())) {
                return;
            }
            collect(entry.getValue(), found, seen);
        });
    }

    private static boolean looksLikeBoid(String value, JsonNode node) {
        if (node.has("id_type") || node.has("type")) {
            return node.has("boid");
        }
        if (node.has("boid")) {
            return true;
        }
        return value.matches("\\d+(?:-\\d+)?");
    }

    private static List<String> setNumbers(JsonNode node) {
        List<String> ids = new ArrayList<>();
        addSetNumber(node.get("set_number"), ids);
        addSetNumber(node.get("set_no"), ids);
        JsonNode idList = node.get("ids");
        if (idList != null && idList.isArray()) {
            for (JsonNode id : idList) {
                String type = firstText(id, "id_type", "type");
                if (type != null && type.toLowerCase(Locale.ROOT).contains("set")) {
                    addSetNumber(id.get("id"), ids);
                    addSetNumber(id.get("value"), ids);
                }
            }
        } else if (idList != null && idList.isObject()) {
            addSetNumber(idList.get("set_number"), ids);
        }
        return ids;
    }

    private static void addSetNumber(JsonNode node, List<String> ids) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> addSetNumber(child, ids));
            return;
        }
        String value = text(node);
        if (value != null) {
            ids.add(value);
        }
    }

    private static void add(String boid, List<String> setNumbers, String name, List<Candidate> found, Set<String> seen) {
        if (boid == null || boid.isBlank() || !seen.add(boid)) {
            return;
        }
        found.add(new Candidate(boid, setNumbers, name));
    }

    private static String names(List<Candidate> candidates) {
        List<String> labels = new ArrayList<>();
        for (Candidate candidate : candidates) {
            String label = candidate.name() == null || candidate.name().isBlank()
                    ? candidate.boid()
                    : candidate.name();
            if (!candidate.setNumbers().isEmpty()) {
                label += " (" + String.join(", ", candidate.setNumbers()) + ")";
            } else if (candidate.name() != null && !candidate.name().isBlank()) {
                label += " [" + candidate.boid() + "]";
            }
            labels.add(label);
        }
        return String.join(", ", labels);
    }

    private static String normalizeSet(String setNumber) {
        if (setNumber == null) {
            return "";
        }
        String trimmed = setNumber.trim().toLowerCase(Locale.ROOT);
        if (trimmed.endsWith("-1")) {
            return trimmed.substring(0, trimmed.length() - 2);
        }
        return trimmed;
    }

    private static String label(String setNumber) {
        return setNumber == null || setNumber.isBlank() ? "(missing)" : setNumber.trim();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node.get(field));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || node.isContainerNode()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    record Candidate(String boid, List<String> setNumbers, String name) {
    }
}
