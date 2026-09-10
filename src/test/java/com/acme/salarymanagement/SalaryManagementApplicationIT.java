package com.acme.salarymanagement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.acme.salarymanagement.support.PostgresIntegrationTest;

class SalaryManagementApplicationIT extends PostgresIntegrationTest {

    @Test
    void the_application_context_loads(@Autowired ApplicationContext context) {
        assertThat(context).isNotNull();
    }
}
