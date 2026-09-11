package com.acme.salarymanagement.identity.adapter.in.seed;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.acme.salarymanagement.identity.domain.Role;

/**
 * The two accounts the demo logs in as, one per role.
 *
 * <p>Fixed ids, so a revision written during one demo still names a user after the next seed, and
 * fixed passwords, which are in the README and are only ever right for a database seeded by this.
 * It runs before the employee seed because the employee seed truncates nothing it owns and the
 * order should still be the obvious one: people who can log in, then people to look at.
 */
@Component
@Profile("seed")
@Order(1)
class DemoUserSeedRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoUserSeedRunner.class);

    /** Demo credentials, published in the README. Not secrets, and marked as not secrets. */
    static final String MANAGER_EMAIL = "hr.manager@acme.example";

    static final String ANALYST_EMAIL = "hr.analyst@acme.example";

    static final String DEMO_PASSWORD = "demo-password";

    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-4000-8000-00000000ffff");
    private static final UUID ANALYST_ID = UUID.fromString("00000000-0000-4000-8000-00000000fffe");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    DemoUserSeedRunner(@Qualifier("seedJdbcTemplate") JdbcTemplate seedJdbcTemplate, PasswordEncoder passwords) {
        this.jdbc = seedJdbcTemplate;
        this.passwords = passwords;
    }

    @Override
    public void run(ApplicationArguments args) {
        upsert(MANAGER_ID, MANAGER_EMAIL, Role.HR_MANAGER);
        upsert(ANALYST_ID, ANALYST_EMAIL, Role.HR_ANALYST);
        LOG.info("Seeded demo users {} and {}", MANAGER_EMAIL, ANALYST_EMAIL);
    }

    private void upsert(UUID id, String email, Role role) {
        // Hashed rather than stored, even here. A demo that keeps plaintext passwords teaches the
        // next person that this is a place plaintext passwords go.
        jdbc.update(
                """
                INSERT INTO app_user (id, email, password_hash, role) VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET email = excluded.email,
                                               password_hash = excluded.password_hash,
                                               role = excluded.role
                """,
                id,
                email,
                passwords.encode(DEMO_PASSWORD),
                role.name());
    }
}
