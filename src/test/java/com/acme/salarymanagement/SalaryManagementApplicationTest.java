package com.acme.salarymanagement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class SalaryManagementApplicationTest {

    @Test
    void the_application_context_loads(ApplicationContext context) {
        assertThat(context).isNotNull();
    }
}
