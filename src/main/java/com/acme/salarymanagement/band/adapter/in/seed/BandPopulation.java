package com.acme.salarymanagement.band.adapter.in.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.acme.salarymanagement.band.domain.SalaryBand;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * The approved range for every role, level and market the org employs someone in.
 *
 * <p>Built from the same pay scale the employees are drawn from, deliberately: bands invented
 * independently of salaries would put most of the company outside its own ranges, and a compa-ratio
 * column that says everyone is underpaid is a column nobody looks at twice. Midpoint is the market
 * rate for that level, and the range is the spread an HR team would actually approve around it.
 */
final class BandPopulation {

    /** Annual midpoint by level, in each market's own currency. Mirrors the seed's pay scale. */
    private static final Map<String, List<String>> MIDPOINTS = Map.of(
            "IN", List.of("800000", "1400000", "2200000", "3200000", "4500000"),
            "US", List.of("75000", "110000", "150000", "190000", "240000"),
            "DE", List.of("55000", "75000", "95000", "120000", "150000"),
            "GB", List.of("48000", "65000", "85000", "105000", "135000"),
            "SG", List.of("70000", "95000", "125000", "155000", "190000"),
            "AU", List.of("80000", "105000", "135000", "165000", "200000"));

    private static final List<String> TITLES = List.of(
            "Software Engineer",
            "Site Reliability Engineer",
            "Data Engineer",
            "Product Manager",
            "Technical Program Manager",
            "Product Designer",
            "UX Researcher",
            "Account Executive",
            "Sales Engineer",
            "Marketing Manager",
            "Content Strategist",
            "Financial Analyst",
            "Accountant",
            "People Partner",
            "Recruiter",
            "Customer Success Manager",
            "Support Engineer");

    /** Fifteen percent below the midpoint and twenty above: wide enough to hold a real spread. */
    private static final BigDecimal FLOOR = new BigDecimal("0.85");

    private static final BigDecimal CEILING = new BigDecimal("1.20");

    private BandPopulation() {}

    static List<SalaryBand> all() {
        List<SalaryBand> bands = new ArrayList<>();
        for (String country : MIDPOINTS.keySet().stream().sorted().toList()) {
            CountryCode market = new CountryCode(country);
            for (String title : TITLES) {
                for (SeniorityLevel level : SeniorityLevel.values()) {
                    bands.add(bandFor(market, new JobTitle(title), level));
                }
            }
        }
        return bands;
    }

    private static SalaryBand bandFor(CountryCode market, JobTitle title, SeniorityLevel level) {
        BigDecimal mid = new BigDecimal(MIDPOINTS.get(market.code()).get(level.ordinal()));
        return new SalaryBand(
                title,
                level,
                market,
                money(mid.multiply(FLOOR), market),
                money(mid, market),
                money(mid.multiply(CEILING), market));
    }

    private static Money money(BigDecimal amount, CountryCode market) {
        return Money.of(amount.setScale(2, RoundingMode.HALF_EVEN), market.currency());
    }
}
