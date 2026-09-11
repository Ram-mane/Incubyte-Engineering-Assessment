package com.acme.salarymanagement.employee.domain;

import static com.acme.salarymanagement.employee.domain.EmployeeMother.anEmployee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Money;

/**
 * The audit guarantee: pay cannot change without producing the record of the change.
 *
 * <p>{@code changeSalaryTo} mutates the current value <em>and</em> returns the {@link
 * SalaryRevision} to be persisted in the same transaction. There is no setter, so there is no code
 * path that moves a salary without also producing the evidence of who moved it and why.
 *
 * <p>Two invariants are enforced elsewhere and are not restated here: I2 (currencies cannot be
 * combined without an explicit rate) belongs to {@code Money} and is covered by {@code MoneyTest};
 * I5 (revisions are immutable once written) is a database grant and is covered by the integration
 * test at task 2.3.
 */
class EmployeeSalaryChangeTest {

    private static final CurrencyCode INR = new CurrencyCode("INR");
    private static final CurrencyCode EUR = new CurrencyCode("EUR");
    private static final UserId HR_MANAGER = new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final Instant WHEN = Instant.parse("2026-09-11T09:15:30Z");
    private static final String NOTE = "Annual merit review, band mid";

    private static Money rupees(String amount) {
        return Money.of(amount, INR);
    }

    @Nested
    class WhatChanges {

        @Test
        void the_employee_holds_the_new_amount_after_the_change() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN);

            assertThat(alice.currentSalary()).isEqualTo(rupees("1380000.00"));
        }

        @Test
        void a_decrease_is_a_legitimate_change() {
            // Pay goes down: a demotion, a correction, a move to a cheaper market.
            var alice = anEmployee().inIndia().earning("1380000.00").build();

            alice.changeSalaryTo(rupees("1200000.00"), ChangeReason.CORRECTION, HR_MANAGER, NOTE, WHEN);

            assertThat(alice.currentSalary()).isEqualTo(rupees("1200000.00"));
        }
    }

    @Nested
    class WhatItRecords {

        @Test
        void changing_salary_produces_exactly_one_revision() {
            // I3. The method returns it rather than publishing it, so a caller cannot forget to
            // persist it without the compiler noticing something was discarded.
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            SalaryRevision revision =
                    alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN);

            assertThat(revision)
                    .as("the revision describes this change and nothing else")
                    .isEqualTo(new SalaryRevision(
                            alice.id(),
                            rupees("1200000.00"),
                            rupees("1380000.00"),
                            ChangeReason.MERIT,
                            HR_MANAGER,
                            WHEN,
                            NOTE));
        }

        @Test
        void the_revision_records_the_amount_held_before_the_change_not_after() {
            // I4. The mutation happens in the same call, so this is the test that catches an
            // implementation that assigns first and reads afterwards.
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            var revision = alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN);

            assertThat(revision.previousAmount()).isEqualTo(rupees("1200000.00"));
            assertThat(revision.newAmount()).isEqualTo(rupees("1380000.00"));
        }

        @Test
        void the_revision_belongs_to_the_employee_whose_pay_changed() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            var revision = alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN);

            assertThat(revision.employeeId()).isEqualTo(alice.id());
        }

        @Test
        void the_revision_names_who_made_the_change_and_why() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            var revision = alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.PROMOTION, HR_MANAGER, NOTE, WHEN);

            assertThat(revision.changedBy()).isEqualTo(HR_MANAGER);
            assertThat(revision.reason()).isEqualTo(ChangeReason.PROMOTION);
        }

        @Test
        void the_revision_is_stamped_with_the_instant_it_was_given() {
            // A revision that cannot say when it happened is not an audit record. The instant is a
            // value rather than a clock, so the method never consults time at all: the same
            // arguments always produce the same revision.
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            var revision = alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN);

            assertThat(revision.changedAt()).isEqualTo(WHEN);
        }

        @Test
        void the_revision_carries_the_note_explaining_the_change() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            var revision = alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN);

            assertThat(revision.note()).isEqualTo(NOTE);
        }

        @Test
        void a_note_is_optional_because_the_reason_is_the_part_that_is_mandatory() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            var revision = alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, null, WHEN);

            assertThat(revision.note()).isNull();
        }

        @Test
        void a_blank_note_is_recorded_as_no_note_at_all() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            var revision = alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, "   ", WHEN);

            assertThat(revision.note()).isNull();
        }

        @Test
        void a_note_keeps_its_text_but_not_its_surrounding_whitespace() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            var revision =
                    alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, "  merit  ", WHEN);

            assertThat(revision.note()).isEqualTo("merit");
        }

        @Test
        void two_revisions_recording_different_changes_are_not_equal() {
            // A revision is a value, not an entity: it is never looked up, updated or referenced,
            // so it has no identity of its own and equality is by what it records.
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            var first = alice.changeSalaryTo(rupees("1300000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN);
            var second = alice.changeSalaryTo(rupees("1400000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN);

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        void two_revisions_recording_the_same_change_are_equal() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();
            var bob = anEmployee()
                    .withId(alice.id())
                    .inIndia()
                    .earning("1200000.00")
                    .build();

            var fromAlice = alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN);
            var fromBob = bob.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN);

            assertThat(fromAlice).isEqualTo(fromBob);
            assertThat(fromAlice).hasSameHashCodeAs(fromBob);
        }

        @Test
        void the_same_change_recorded_twice_is_the_same_revision_because_nothing_is_minted_inside() {
            // Guards the property that makes the seed generator deterministic: changeSalaryTo is a
            // pure function of its arguments, so identical input produces an identical record.
            var alice = anEmployee().inIndia().earning("1200000.00").build();
            var twin = anEmployee()
                    .withId(alice.id())
                    .inIndia()
                    .earning("1200000.00")
                    .build();

            assertThat(alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN))
                    .isEqualTo(twin.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN));
        }

        @Test
        void successive_revisions_chain_the_previous_amount_to_the_one_before_it() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            var first = alice.changeSalaryTo(rupees("1300000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN);
            var second = alice.changeSalaryTo(rupees("1400000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN);

            assertThat(second.previousAmount()).isEqualTo(first.newAmount());
        }
    }

    @Nested
    class OutsideIndia {

        // Until this existed every employee in the suite was Indian, so replacing
        // country.currency() with the literal "INR" passed one hundred and four tests: I9 was a
        // test that a hardcoded string equalled itself.

        @Test
        void an_employee_in_germany_is_paid_and_re_paid_in_euros() {
            var klaus = anEmployee().inGermany().build();

            var revision =
                    klaus.changeSalaryTo(Money.of("92000.00", EUR), ChangeReason.PROMOTION, HR_MANAGER, NOTE, WHEN);

            assertThat(klaus.currentSalary()).isEqualTo(Money.of("92000.00", EUR));
            assertThat(revision.previousAmount()).isEqualTo(Money.of("85000.00", EUR));
        }

        @Test
        void an_employee_in_germany_cannot_be_moved_to_rupees() {
            var klaus = anEmployee().inGermany().build();

            assertThatThrownBy(() ->
                            klaus.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DE")
                    .hasMessageContaining("EUR")
                    .hasMessageContaining("INR");
        }
    }

    @Nested
    class WhatItRefuses {

        @Test
        void changing_salary_to_the_same_amount_is_rejected_as_a_no_op() {
            // I6. A no-op that produced a revision would fill the audit log with changes that
            // changed nothing, and make "when did their pay last move" unanswerable.
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            assertThatThrownBy(() ->
                            alice.changeSalaryTo(rupees("1200000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already");
        }

        @Test
        void the_same_amount_written_at_a_different_scale_is_still_a_no_op() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            assertThatThrownBy(
                            () -> alice.changeSalaryTo(rupees("1200000.0"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void changing_salary_to_zero_is_rejected() {
            // I1, enforced here rather than on Money: zero is legitimate money, and not a
            // legitimate salary (D028).
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            assertThatThrownBy(
                            () -> alice.changeSalaryTo(rupees("0.00"), ChangeReason.CORRECTION, HR_MANAGER, NOTE, WHEN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        void changing_salary_to_a_negative_amount_is_rejected() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            assertThatThrownBy(() ->
                            alice.changeSalaryTo(rupees("-1.00"), ChangeReason.CORRECTION, HR_MANAGER, NOTE, WHEN))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void a_change_without_a_reason_is_rejected() {
            // I7. The reason is the difference between an audit log and a list of numbers.
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            assertThatThrownBy(() -> alice.changeSalaryTo(rupees("1380000.00"), null, HR_MANAGER, NOTE, WHEN))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void a_change_without_an_actor_is_rejected() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            assertThatThrownBy(() -> alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, null, NOTE, WHEN))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void a_change_without_a_timestamp_is_rejected() {
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            assertThatThrownBy(() ->
                            alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void a_change_to_a_currency_other_than_the_country_currency_is_rejected() {
            // I9, on change as well as at construction.
            var alice = anEmployee().inIndia().earning("1200000.00").build();

            assertThatThrownBy(() ->
                            alice.changeSalaryTo(Money.of("45000.00", EUR), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INR")
                    .hasMessageContaining("EUR");
        }

        @Test
        void a_terminated_employee_rejects_a_salary_change() {
            // I8, on status rather than on termination-date arithmetic, so no clock is consulted
            // to answer it (D041).
            var alice =
                    anEmployee().inIndia().earning("1200000.00").terminated().build();

            assertThatThrownBy(() ->
                            alice.changeSalaryTo(rupees("1380000.00"), ChangeReason.MERIT, HR_MANAGER, NOTE, WHEN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TERMINATED");
        }

        /**
         * The invariant behind every rejection above, over all five of them rather than one.
         *
         * <p>Refusing must not half-apply. Asserting this on the currency path alone let the
         * assignment move above the other guards while forty-one tests stayed green: a terminated
         * employee's pay moved, a zero salary was applied, and the exception was thrown afterwards
         * with no revision to account for it.
         */
        @ParameterizedTest(name = "{0}")
        @MethodSource("everyRejection")
        void a_rejected_change_moves_nothing_and_records_nothing(
                String scenario,
                EmploymentStatus status,
                Money attempted,
                ChangeReason reason,
                Class<? extends Throwable> expected) {

            var alice =
                    anEmployee().inIndia().earning("1200000.00").status(status).build();
            AtomicReference<SalaryRevision> produced = new AtomicReference<>();

            assertThatThrownBy(() -> produced.set(alice.changeSalaryTo(attempted, reason, HR_MANAGER, NOTE, WHEN)))
                    .isInstanceOf(expected);

            assertThat(produced.get())
                    .as("a refused change must produce no revision at all")
                    .isNull();
            assertThat(alice.currentSalary())
                    .as("a refused change must leave the salary exactly as it was")
                    .isEqualTo(rupees("1200000.00"));
        }

        static Stream<Arguments> everyRejection() {
            return Stream.of(
                    Arguments.of(
                            "zero",
                            EmploymentStatus.ACTIVE,
                            Money.of("0.00", INR),
                            ChangeReason.CORRECTION,
                            IllegalArgumentException.class),
                    Arguments.of(
                            "negative",
                            EmploymentStatus.ACTIVE,
                            Money.of("-1.00", INR),
                            ChangeReason.CORRECTION,
                            IllegalArgumentException.class),
                    Arguments.of(
                            "same amount, a no-op",
                            EmploymentStatus.ACTIVE,
                            Money.of("1200000.00", INR),
                            ChangeReason.MERIT,
                            IllegalArgumentException.class),
                    Arguments.of(
                            "a currency the country does not pay in",
                            EmploymentStatus.ACTIVE,
                            Money.of("45000.00", EUR),
                            ChangeReason.MERIT,
                            IllegalArgumentException.class),
                    Arguments.of(
                            "a terminated employee",
                            EmploymentStatus.TERMINATED,
                            Money.of("1380000.00", INR),
                            ChangeReason.MERIT,
                            IllegalStateException.class));
        }
    }
}
