package com.acme.salarymanagement.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * How a cross-currency total is built, and how it must not be - see ADR-0013.
 *
 * <p>PostgreSQL computes the dashboard total as {@code sum(amount * rate)} over {@code numeric},
 * exact throughout and rounded once. That makes sum-then-convert the definition of correct rather
 * than one candidate answer, and per-row conversion a defect whose size grows with headcount.
 */
class CurrencyNormalisationPropertyTest {

    private static final CurrencyCode INR = new CurrencyCode("INR");
    private static final CurrencyCode EUR = new CurrencyCode("EUR");
    private static final CurrencyCode JPY = new CurrencyCode("JPY");
    private static final CurrencyCode USD = new CurrencyCode("USD");
    private static final LocalDate TENTH_OF_SEPTEMBER = LocalDate.of(2026, 9, 10);

    /** USD is absent deliberately: converting dollars to dollars needs no rate. */
    private static final Map<CurrencyCode, ExchangeRate> TO_DOLLARS = Map.of(
            INR, new ExchangeRate(INR, USD, new BigDecimal("0.0120"), TENTH_OF_SEPTEMBER),
            EUR, new ExchangeRate(EUR, USD, new BigDecimal("1.0850"), TENTH_OF_SEPTEMBER),
            JPY, new ExchangeRate(JPY, USD, new BigDecimal("0.0068"), TENTH_OF_SEPTEMBER));

    /**
     * P1, exact. The sanctioned path: sum within a currency, convert the subtotal once. Order of
     * summation cannot matter, and the result must equal a full-precision reference rounded once -
     * which is what PostgreSQL's sum(amount * rate) computes. Zero tolerance.
     */
    @Property
    void summing_one_currency_then_converting_once_is_exact_and_order_independent(
            @ForAll("salariesInOneCurrency") List<Money> salaries, @ForAll long reordering) {

        CurrencyCode currency = salaries.get(0).currency();
        ExchangeRate rate = TO_DOLLARS.get(currency);

        Money asGiven = convertSubtotalOnce(salaries, rate);
        Money reordered = convertSubtotalOnce(shuffled(salaries, reordering), rate);

        BigDecimal reference = salaries.stream()
                .map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(rate.rate())
                .setScale(USD.scale(), RoundingMode.HALF_EVEN);

        assertThat(asGiven)
                .as("%d salaries in %s, reordered", salaries.size(), currency.code())
                .isEqualTo(reordered);
        assertThat(asGiven.amount())
                .as("must equal the full-precision total rounded once, as the SQL aggregate does")
                .isEqualByComparingTo(reference);
    }

    /**
     * P2, bounded. Documents a prohibition rather than permitting the drift: this tolerance is the
     * measured size of a known defect, not a budget a caller may spend. Per-row conversion rounds n
     * times where the sanctioned path rounds once per currency, so the two diverge by up to half a
     * minor unit per rounding - which is why no total is built this way.
     */
    @Property
    void per_row_conversion_accumulates_error_and_must_not_be_used_for_totals(
            @ForAll("salariesInMixedCurrencies") List<Money> salaries) {

        Money perRow = salaries.stream().map(this::inDollars).reduce(Money.of("0.00", USD), Money::plus);

        Map<CurrencyCode, Money> byCurrency = new LinkedHashMap<>();
        salaries.forEach(salary -> byCurrency.merge(salary.currency(), salary, Money::plus));
        Money sanctioned = byCurrency.values().stream().map(this::inDollars).reduce(Money.of("0.00", USD), Money::plus);

        BigDecimal bound = new BigDecimal("0.005").multiply(BigDecimal.valueOf(salaries.size() + byCurrency.size()));

        assertThat(perRow.minus(sanctioned).amount().abs())
                .as("%d salaries in %d currencies", salaries.size(), byCurrency.size())
                .isLessThanOrEqualTo(bound);
    }

    private Money convertSubtotalOnce(List<Money> salaries, ExchangeRate rate) {
        Money subtotal = salaries.stream().reduce(Money::plus).orElseThrow();
        return subtotal.convertTo(USD, rate);
    }

    private List<Money> shuffled(List<Money> salaries, long seed) {
        List<Money> copy = new ArrayList<>(salaries);
        Collections.shuffle(copy, new Random(seed));
        return copy;
    }

    private Money inDollars(Money salary) {
        return salary.convertTo(USD, TO_DOLLARS.get(salary.currency()));
    }

    @Provide
    Arbitrary<List<Money>> salariesInOneCurrency() {
        return Arbitraries.of(INR, EUR, JPY).flatMap(currency -> amounts()
                .map(amount -> Money.of(amount, currency))
                .list()
                .ofMinSize(1)
                .ofMaxSize(40));
    }

    @Provide
    Arbitrary<List<Money>> salariesInMixedCurrencies() {
        return Combinators.combine(amounts(), Arbitraries.of(INR, EUR, JPY, USD))
                .as(Money::of)
                .list()
                .ofMinSize(1)
                .ofMaxSize(40);
    }

    private Arbitrary<BigDecimal> amounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("1.00"), new BigDecimal("50000000.00"))
                .ofScale(2);
    }
}
