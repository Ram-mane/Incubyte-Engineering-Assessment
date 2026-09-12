package com.acme.salarymanagement.band.application.port.in;

import java.math.BigDecimal;

import com.acme.salarymanagement.band.domain.Position;
import com.acme.salarymanagement.shared.Money;

/**
 * One employee's approved range, and where their pay sits in it.
 *
 * <p>A view rather than the {@code SalaryBand} aggregate, because the modules that ask for this
 * are not allowed to reach into this one's domain. They get the numbers and the verdict; the
 * arithmetic stays here.
 *
 * @param position the label a reader acts on. Reported for any salary, including one far outside
 *     the range - that is the feature, not an error condition.
 */
public record BandForSalary(Money min, Money mid, Money max, BigDecimal compaRatio, Position position) {}
