package com.acme.salarymanagement.band.adapter.in.web;

import com.acme.salarymanagement.band.application.port.in.BandForSalary;
import com.acme.salarymanagement.shared.Money;

/**
 * The approved range for a role, and where a salary sits in it.
 *
 * <p>{@code defined} is false rather than the endpoint 404ing when no band exists: a role without
 * an approved range is an ordinary state of the data, not a missing resource, and the screen has
 * something honest to say about it.
 */
record BandResponse(
        boolean defined, MoneyResponse min, MoneyResponse mid, MoneyResponse max, String compaRatio, String position) {

    static final BandResponse NONE = new BandResponse(false, null, null, null, null, null);

    static BandResponse of(BandForSalary band) {
        return new BandResponse(
                true,
                MoneyResponse.of(band.min()),
                MoneyResponse.of(band.mid()),
                MoneyResponse.of(band.max()),
                // A string for the same reason money is: the browser parses it for display only.
                band.compaRatio().toPlainString(),
                band.position().name());
    }

    record MoneyResponse(String amount, String currency) {

        static MoneyResponse of(Money money) {
            return new MoneyResponse(
                    money.amount().toPlainString(), money.currency().code());
        }
    }
}
