package com.acme.salarymanagement.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Recognising a pooled endpoint, so the failure explains itself.
 *
 * <p>This is the difference between a deploy log that says "permission denied for table
 * flyway_schema_history" and one that says the datasource points at a pooler where SET ROLE
 * leaks between clients. The first cost an afternoon.
 */
class DatabaseIdentityCheckTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "jdbc:postgresql://ep-cool-name-123456-pooler.ap-southeast-1.aws.neon.tech/salary",
                "jdbc:postgresql://ep-cool-name-123456-POOLER.ap-southeast-1.aws.neon.tech/salary",
                "jdbc:postgresql://pgbouncer.internal:6432/salary"
            })
    void a_pooled_endpoint_is_recognised(String url) {
        assertThat(DatabaseIdentityCheck.namesAPooler(url)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "jdbc:postgresql://ep-cool-name-123456.ap-southeast-1.aws.neon.tech/salary",
                "jdbc:postgresql://localhost:5432/salary_management",
                "jdbc:postgresql://db.internal:5432/poolerish"
            })
    void a_direct_endpoint_is_not_mistaken_for_one(String url) {
        // "poolerish" is in the last one on purpose: matching the bare word would make the
        // diagnosis confidently wrong, which is worse than no diagnosis.
        assertThat(DatabaseIdentityCheck.namesAPooler(url)).isFalse();
    }

    @Test
    void an_absent_url_is_not_a_pooler() {
        assertThat(DatabaseIdentityCheck.namesAPooler(null)).isFalse();
    }
}
