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
        String snake = source.trim().replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        return DistributionDimension.valueOf(snake.toUpperCase(Locale.ROOT));
    }
}
