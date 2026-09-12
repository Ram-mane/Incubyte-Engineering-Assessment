package com.acme.salarymanagement.analytics.adapter.in.web;

import java.util.Locale;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.acme.salarymanagement.analytics.application.port.in.DistributionDimension;

/**
 * Reads {@code ?groupBy=jobTitle} as {@link DistributionDimension#JOB_TITLE}.
 *
 * <p>Same job as {@link BreakdownDimensionConverter} and separate for the same reason the enums are
 * separate: the two endpoints accept different sets, and one converter over a merged enum would let
 * {@code ?groupBy=country} reach the distribution query, which has no meaning for it.
 *
 * <p>The API spells the dimension in camel case, so {@code jobTitle} has to reach {@code JOB_TITLE}
 * rather than {@code JOBTITLE}: the underscore is inserted before a capital, then the whole thing
 * upper-cased.
 */
@Component
class DistributionDimensionConverter implements Converter<String, DistributionDimension> {

    @Override
    public DistributionDimension convert(String source) {
        // openapi says "read case-insensitively", and `jobtitle` has to honour that as much as
        // `jobTitle` does - inserting the underscore only before a capital made the claim true of
        // three spellings and false of the fourth.
        String letters = source.trim().replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        for (DistributionDimension dimension : DistributionDimension.values()) {
            if (dimension.name().replace("_", "").equals(letters)) {
                return dimension;
            }
        }
        throw new IllegalArgumentException(
                "No such distribution dimension: %s. Expected jobTitle or department.".formatted(source));
    }
}
