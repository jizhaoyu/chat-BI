package com.jizhaoyu.chatbi.domain.catalog;

import java.util.List;
import java.util.Objects;

public record SemanticMetadata(String businessName, List<String> synonyms, SensitivityLevel sensitivity) {
    public SemanticMetadata {
        businessName = normalize(businessName);
        synonyms = synonyms == null ? List.of() : synonyms.stream()
                .map(SemanticMetadata::normalize)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        Objects.requireNonNull(sensitivity, "sensitivity");
    }

    public static SemanticMetadata physicalOnly() {
        return new SemanticMetadata("", List.of(), SensitivityLevel.PUBLIC);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
