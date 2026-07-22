package com.srm.creditengine.assignor.domain;

/** Pure domain value-object for normalising and validating a Tax ID. */
public final class TaxId {

    private TaxId() {}

    /**
     * Strips all non-alphanumeric characters from {@code raw} and upper-cases the result.
     *
     * @throws IllegalArgumentException if the normalised value is blank
     */
    public static String normalize(String raw) {
        String value = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Tax ID is required");
        }
        return value;
    }
}
