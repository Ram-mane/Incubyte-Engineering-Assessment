package com.acme.salarymanagement.band.application.port.in;

import java.util.Optional;

import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * The approved range for a role, at a level, in a market - and where a salary sits in it.
 *
 * <p>Empty when no band is defined for that combination. Not every role has an approved range and
 * the screen says so; a zero band would put the employee above a maximum nobody set.
 */
public interface FindSalaryBand {

    Optional<BandForSalary> of(JobTitle jobTitle, SeniorityLevel level, CountryCode country, Money salary);
}
