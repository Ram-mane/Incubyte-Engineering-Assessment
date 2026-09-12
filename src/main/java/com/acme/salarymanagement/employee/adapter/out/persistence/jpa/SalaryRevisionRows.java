package com.acme.salarymanagement.employee.adapter.out.persistence.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

/**
 * The two shapes of the same read, kept side by side because the difference is the point.
 *
 * <p>{@link #findByEmployeeIdOrderByChangedAtDesc} is the obvious one and is an N+1: it selects the
 * revisions, then one more select per row the moment anything touches {@code changedBy}.
 * {@link #findEagerlyByEmployeeIdOrderByChangedAtDesc} asks for the association in the same query.
 * The statement counts for both
 * are in {@code docs/evidence/09-jpa-read-path.txt}, measured by the same harness the rest of the
 * suite uses.
 *
 * <p>Extends {@code Repository}, not {@code JpaRepository}: inheriting {@code save} and
 * {@code delete} on the append-only log would put two write methods on the audit trail that no
 * caller may use and the database would refuse anyway.
 *
 * <p>Not named {@code ...Repository}, and ArchUnit is why - correctly. In this codebase a
 * {@code Repository} is a port, declared by the application layer that needs it. This is a
 * Spring Data query interface living inside the adapter: an implementation detail of one port
 * ({@code SalaryRevisionReader}), not a port itself. The rule caught the name claiming otherwise.
 */
interface SalaryRevisionRows extends Repository<SalaryRevisionEntity, UUID> {

    List<SalaryRevisionEntity> findByEmployeeIdOrderByChangedAtDesc(UUID employeeId, Limit limit);

    @EntityGraph(attributePaths = "changedBy")
    List<SalaryRevisionEntity> findEagerlyByEmployeeIdOrderByChangedAtDesc(UUID employeeId, Limit limit);
}
