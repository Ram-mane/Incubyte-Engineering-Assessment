package com.acme.salarymanagement.band.application.port.out;

import java.util.List;

import com.acme.salarymanagement.band.domain.SalaryBand;

/** Bulk loading of approved bands. Named so `analytics` can be forbidden to depend on it. */
public interface BandWriteRepository {

    void replaceAllWith(List<SalaryBand> bands);
}
