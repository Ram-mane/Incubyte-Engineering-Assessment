package com.acme.salarymanagement.band.adapter.in.web;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.salarymanagement.band.application.port.in.FindSalaryBand;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * The approved pay range for a role, at a level, in a market.
 *
 * <p>Takes the role rather than an employee id, which is why it lives here and not on the employee
 * resource: a band belongs to a role, and reaching for an employee would make this module depend
 * on that one for nothing it needs. The caller already holds the employee when it asks.
 *
 * <p>Read-only, and nothing on any write path consults it. A pay change far outside the band
 * succeeds exactly as one inside it does - see {@code BandsAreDisplayedNeverEnforcedIT}.
 *
 * <p><b>A POST that reads nothing and writes nothing.</b> Placing a salary in its band needs the
 * salary, and a query string is the one part of a request that reaches the access log, the browser
 * history and the {@code Referer} of everything the page loads afterwards. "No salary figures in
 * log output" is a rule about where pay ends up, not about which logger wrote it - so the figure
 * travels in a body. Nothing is created; the 200 is the answer, not a resource.
 */
@RestController
@RequestMapping("/api/v1/bands")
class BandController {

    private final FindSalaryBand bands;

    BandController(FindSalaryBand bands) {
        this.bands = bands;
    }

    @PostMapping("/position")
    BandResponse forRole(@RequestBody BandQuery query) {
        Money paid = Money.of(
                new BigDecimal(query.salary()),
                new CurrencyCode(query.currency().toUpperCase(Locale.ROOT)));
        return bands.of(
                        new JobTitle(query.jobTitle().trim()),
                        SeniorityLevel.valueOf(query.level().toUpperCase(Locale.ROOT)),
                        new CountryCode(query.country().toUpperCase(Locale.ROOT)),
                        paid)
                .map(BandResponse::of)
                .orElse(BandResponse.NONE);
    }

    /** @param salary a decimal string, like every other amount crossing this API */
    record BandQuery(String jobTitle, String level, String country, String salary, String currency) {}
}
