package com.acme.salarymanagement.analytics.adapter.in.web;

import java.util.Locale;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.acme.salarymanagement.analytics.application.port.in.BreakdownDimension;

/**
 * Reads {@code ?groupBy=department} as {@link BreakdownDimension#DEPARTMENT}.
 *
 * <p>`04-API-DESIGN.md` documents the dimension in lower case and Spring's default enum binding is
 * case-sensitive, so without this the documented request is a 400. Conversion happens here rather
 * than in the handler so the controller only ever holds the enum — a string that can name a column
 * never exists in the web layer.
 *
 * <p>An unknown value throws {@link IllegalArgumentException}, which the request binding turns into
 * a 400. That is the intended answer: the set of dimensions is closed.
 */
@Component
class BreakdownDimensionConverter implements Converter<String, BreakdownDimension> {

    @Override
    public BreakdownDimension convert(String source) {
        return BreakdownDimension.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
