package com.acme.salarymanagement.band.adapter.in.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.acme.salarymanagement.band.application.port.out.BandWriteRepository;
import com.acme.salarymanagement.band.domain.SalaryBand;

/** Loads the approved ranges. Runs after the employees, so the two are seeded from one pay scale. */
@Component
@Profile("seed")
@Order(3)
class BandSeedRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BandSeedRunner.class);

    private final BandWriteRepository bands;

    BandSeedRunner(BandWriteRepository bands) {
        this.bands = bands;
    }

    @Override
    public void run(ApplicationArguments args) {
        java.util.List<SalaryBand> approved = BandPopulation.all();
        bands.replaceAllWith(approved);
        int count = approved.size();
        LOG.info("Seeded {} salary bands", count);
    }
}
