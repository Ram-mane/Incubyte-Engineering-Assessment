package com.acme.salarymanagement.band.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Where a salary sits relative to its band: the ratio, and the label a reader acts on.
 *
 * <p>Absent rather than zero when no band matches the employee's role. A compa-ratio of {@code 0}
 * would read as "paid nothing" and {@code -1} is not a ratio, so the absence is modelled by the
 * caller holding an {@code Optional<BandPosition>} rather than by a sentinel inside this type.
 */
public record BandPosition(BigDecimal compaRatio, Position position) {

    public BandPosition {
        Objects.requireNonNull(compaRatio, "a compa-ratio is required");
        Objects.requireNonNull(position, "a position is required");
    }
}
