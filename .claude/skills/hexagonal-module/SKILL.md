---
name: hexagonal-module
description: How to add a use case, endpoint, or module in this codebase without violating the layering. Use whenever creating a new feature, service, controller, repository or port.
---

# Adding a use case

Build it in this order. The order is the point: it forces the domain to be designed before the
plumbing, and it means the test exists before the implementation.

## 1. Inbound port — `<module>/application/port/in/`
```java
public interface RecordSalaryChangeUseCase {
    SalaryRecordView record(RecordSalaryChangeCommand command);
}
public record RecordSalaryChangeCommand(
    EmployeeId employeeId, Money amount, LocalDate effectiveFrom,
    ChangeReason reason, String note) {
    public RecordSalaryChangeCommand { /* validate shape here, not business rules */ }
}
```
Commands and views are records. They are the module's contract; they never expose JPA entities.

## 2. Outbound ports — `<module>/application/port/out/`
Declare what the use case *needs*, in the language of the domain:
`Employee load(EmployeeId id)` and `void append(SalaryRevision r)`, not
`List<SalaryRevisionEntity> findByEmployeeId(Long id)`.

**The port lives in `application`, not in the persistence package.** If you put it next to its JPA
implementation you have inverted nothing.

## 3. Domain
Any real rule goes in a domain object, not the service. If `Employee` should know it, put it
there. The service should read like a paragraph: load, apply, save, publish.

## 4. Test the service against fake ports
An in-memory `FakeSalaryRepository` implementing the port. No Mockito for repositories — a fake that
behaves correctly is more readable and catches ordering bugs a mock cannot.

## 5. Service — `<module>/application/service/`
```java
@Service
class RecordSalaryChangeService implements RecordSalaryChangeUseCase {
    @Override @Transactional
    @PreAuthorize("hasRole('HR_MANAGER')")
    public SalaryRecordView record(RecordSalaryChangeCommand cmd) { ... }
}
```
Package-private class, public interface. Authorisation goes **here**, not only on the controller.

## 6. Persistence adapter — `<module>/adapter/out/persistence/`
`XxxJpaEntity` (mutable, framework-shaped) and `XxxRepositoryAdapter` implementing the port, with an
explicit mapper. Entities never escape this package. A separate Spring Data interface does the query.

## 7. Web adapter — `<module>/adapter/in/web/`
Controller does four things and nothing else: bind, validate shape, call the use case, map the
result. No business logic, no repository, no `@Transactional`.

## 8. Flyway migration
New file, forward-only, never edit an applied one. Add the indexes in the same migration as the
table, with a comment saying which query each serves.

## 9. Integration test
Against Testcontainers Postgres, through the real HTTP layer. Assert the outcome and the statement
count.

## Checklist
- [ ] Port declared in `application`, implemented in `adapter`
- [ ] Domain object holds the rule, service only orchestrates
- [ ] No entity outside its persistence package
- [ ] `@PreAuthorize` on the use case
- [ ] `openapi.yaml` updated
- [ ] `mvn verify` green including ArchUnit
