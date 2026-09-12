package com.acme.salarymanagement.band.application.port.out;

import java.util.Optional;

import com.acme.salarymanagement.band.domain.SalaryBand;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * Reading bands. Separate from {@link BandWriteRepository}, which only the seed uses: the
 * application holds SELECT on this table and nothing else (D103).
 */
public interface SalaryBandRepository {

    Optional<SalaryBand> forRole(JobTitle jobTitle, SeniorityLevel level, CountryCode country);
}
