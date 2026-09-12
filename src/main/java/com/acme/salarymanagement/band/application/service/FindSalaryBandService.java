package com.acme.salarymanagement.band.application.service;

import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.band.application.port.in.BandForSalary;
import com.acme.salarymanagement.band.application.port.in.FindSalaryBand;
import com.acme.salarymanagement.band.application.port.out.SalaryBandRepository;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * Finds the band and asks it where the salary sits.
 *
 * <p>Nothing here refuses anything. The classification is computed and returned; what to do about
 * an employee above their maximum is the HR Manager's judgement, which is what "displayed, never
 * enforced" means in practice.
 */
@Service
class FindSalaryBandService implements FindSalaryBand {

    private final SalaryBandRepository bands;

    FindSalaryBandService(SalaryBandRepository bands) {
        this.bands = bands;
    }

    @Override
    @PreAuthorize("hasAnyRole('HR_MANAGER','HR_ANALYST')")
    @Transactional(readOnly = true)
    public Optional<BandForSalary> of(JobTitle jobTitle, SeniorityLevel level, CountryCode country, Money salary) {
        return bands.forRole(jobTitle, level, country).map(band -> {
            var position = band.positionOf(salary);
            return new BandForSalary(band.min(), band.mid(), band.max(), position.compaRatio(), position.position());
        });
    }
}
