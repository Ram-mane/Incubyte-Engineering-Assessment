package com.acme.salarymanagement.employee.domain;

import static com.acme.salarymanagement.employee.domain.EmployeeMother.anEmployee;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Money;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The audit guarantee, stated over any history rather than one example.
 *
 * <p>An example test shows that a change produces a revision. This shows that no sequence of
 * changes, of any length, can produce a log with a gap in it - which is the claim the product
 * actually makes when it says every movement in pay is accounted for.
 */
class SalaryChangeAuditChainPropertyTest {

    private static final CurrencyCode INR = new CurrencyCode("INR");
    private static final Money STARTING_SALARY = Money.of("1000000.00", INR);
    private static final UserId HR_MANAGER = new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final Instant WHEN = Instant.parse("2026-09-11T09:15:30Z");

    @Property
    void the_audit_chain_accounts_for_every_accepted_change(@ForAll("salaryHistories") List<Money> amounts) {
        var alice = anEmployee().inIndia().earningExactly(STARTING_SALARY).build();

        // Rejected attempts are interleaved deliberately. Counting only successes made the size
        // assertion unfalsifiable - the loop added one element per amount, so it could fail only
        // if changeSalaryTo threw. A no-op that wrongly returned a revision now lengthens the log
        // and the count catches it.
        List<SalaryRevision> revisions = new ArrayList<>();
        for (Money amount : amounts) {
            record(revisions, alice, alice.currentSalary());
            record(revisions, alice, Money.of("0.00", INR));
            record(revisions, alice, amount);
        }

        assertThat(revisions)
                .as("one revision per accepted change, and none for the rejected ones")
                .hasSameSizeAs(amounts);

        assertThat(revisions.get(0).previousAmount())
                .as("the first revision starts from what the employee was hired on")
                .isEqualTo(STARTING_SALARY);

        for (int i = 1; i < revisions.size(); i++) {
            assertThat(revisions.get(i).previousAmount())
                    .as("revision %d must continue from revision %d, leaving no gap", i, i - 1)
                    .isEqualTo(revisions.get(i - 1).newAmount());
        }

        assertThat(alice.currentSalary())
                .as("the employee ends on the amount the last revision recorded")
                .isEqualTo(revisions.get(revisions.size() - 1).newAmount());

        assertThat(revisions)
                .as("every revision belongs to the employee whose pay moved")
                .allSatisfy(revision -> assertThat(revision.employeeId()).isEqualTo(alice.id()));
    }

    /** Appends whatever the aggregate returns, so a revision produced by a refused change counts. */
    private void record(List<SalaryRevision> revisions, Employee employee, Money amount) {
        try {
            revisions.add(employee.changeSalaryTo(amount, ChangeReason.MERIT, HR_MANAGER, null, WHEN));
        } catch (IllegalArgumentException | IllegalStateException refused) {
            // a refused change must leave no trace, which the size assertion checks
        }
    }

    /**
     * Consecutive amounts must differ, or the change is rejected as a no-op and there is no
     * revision to chain - so the generator produces histories of accepted changes, which is what
     * the property is about.
     */
    @Provide
    Arbitrary<List<Money>> salaryHistories() {
        return Arbitraries.integers()
                .between(100_000, 9_000_000)
                .filter(amount -> amount != 1_000_000)
                .map(amount -> Money.of(amount + ".00", INR))
                .list()
                .uniqueElements()
                .ofMinSize(1)
                .ofMaxSize(25);
    }
}
