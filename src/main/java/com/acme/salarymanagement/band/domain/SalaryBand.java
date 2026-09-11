package com.acme.salarymanagement.band.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyMismatchException;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * The approved pay range for one role, at one level, in one market.
 *
 * <p><strong>Displayed, never enforced.</strong> Nothing here blocks a salary outside the range or
 * raises an alert: {@link #positionOf} reports where someone sits and leaves the judgement to the
 * HR Manager. That was confirmed with the customer; enforcement is future scope.
 *
 * <p>No identity of its own. A band is found by its natural key - role, level, country, which the
 * data model makes unique - and referenced by nothing, so the surrogate key belongs to the table
 * rather than to the domain. Same reasoning as {@code SalaryRevision} (D079).
 */
public record SalaryBand(
        JobTitle jobTitle, SeniorityLevel level, CountryCode country, Money min, Money mid, Money max) {

    /**
     * Four places, because {@code 1234567 / 1250000} recurs and has to round somewhere: precise
     * enough to sort a directory by, not so precise that it implies an accuracy the inputs lack.
     */
    private static final int COMPA_RATIO_SCALE = 4;

    public SalaryBand {
        Objects.requireNonNull(jobTitle, "a job title is required");
        Objects.requireNonNull(level, "a seniority level is required");
        Objects.requireNonNull(country, "a country is required");
        Objects.requireNonNull(min, "a band minimum is required");
        Objects.requireNonNull(mid, "a band midpoint is required");
        Objects.requireNonNull(max, "a band maximum is required");

        requireOneCurrency(min, mid, max);
        // I10: 0 < min <= mid <= max. Positive for all three, not only the midpoint the ratio
        // divides by - a band with a zero minimum describes no range anyone could be paid within.
        if (!min.isPositive() || !mid.isPositive() || !max.isPositive()) {
            throw new IllegalArgumentException("every bound of a band must be greater than zero");
        }
        if (mid.amount().compareTo(min.amount()) < 0 || max.amount().compareTo(mid.amount()) < 0) {
            throw new IllegalArgumentException("a band must run min <= mid <= max");
        }
    }

    /**
     * Where a salary sits in this band. Returns a classification for any salary, including one far
     * outside the range: reporting that is the feature.
     */
    public BandPosition positionOf(Money salary) {
        Objects.requireNonNull(salary, "a salary is required");
        requireOneCurrency(salary, mid);

        BigDecimal compaRatio = salary.amount().divide(mid.amount(), COMPA_RATIO_SCALE, RoundingMode.HALF_EVEN);
        return new BandPosition(compaRatio, classify(salary, compaRatio));
    }

    private Position classify(Money salary, BigDecimal compaRatio) {
        // Outside the range is a different statement from far from the midpoint, so the bounds are
        // asked first: an employee under the minimum is BELOW_MIN whatever their compa-ratio.
        if (salary.amount().compareTo(min.amount()) < 0) {
            return Position.BELOW_MIN;
        }
        if (salary.amount().compareTo(max.amount()) > 0) {
            return Position.ABOVE_MAX;
        }
        if (compaRatio.compareTo(new BigDecimal("0.9")) < 0) {
            return Position.LOW;
        }
        if (compaRatio.compareTo(new BigDecimal("1.1")) > 0) {
            return Position.HIGH;
        }
        return Position.WITHIN;
    }

    private static void requireOneCurrency(Money first, Money... others) {
        for (Money other : others) {
            if (!first.currency().equals(other.currency())) {
                throw new CurrencyMismatchException("a band and the salaries it ranges over must share one currency, "
                        + "but found %s and %s"
                                .formatted(
                                        first.currency().code(),
                                        other.currency().code()));
            }
        }
    }
}
