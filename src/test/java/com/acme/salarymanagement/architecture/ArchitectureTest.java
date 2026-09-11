package com.acme.salarymanagement.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.acme.salarymanagement.employee.domain.SalaryRevision;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * The rules from docs/03-ARCHITECTURE.md section 4, made executable.
 *
 * <p>An architecture nobody can violate accidentally is an architecture; one described only in a
 * diagram is a hope. These fail the build, which is the whole difference.
 */
@AnalyzeClasses(packages = "com.acme.salarymanagement", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // An allowlist, not a denylist. Naming the frameworks to forbid only forbids the ones
    // someone thought of: slf4j, Guava or the next convenient library would all have passed.
    // Stating what the domain may depend on - the JDK and itself - has no such gap.
    @ArchTest
    static final ArchRule the_domain_and_shared_kernel_depend_on_nothing_but_the_jdk = noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..shared..")
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages("java..", "javax..", "..domain..", "..shared..")
            .because("the domain is plain Java, so its rules can be tested in milliseconds "
                    + "without a container and cannot be bent to suit a framework - and shared "
                    + "is inside that boundary, because the domain depends on it");

    @ArchTest
    static final ArchRule the_domain_does_not_reach_up_into_the_application_layer = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..")
            .because("dependencies point inward: a use case knows its aggregate, never the reverse");

    @ArchTest
    static final ArchRule the_application_layer_never_touches_an_adapter = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter..")
            .because("the application layer depends on ports it owns, so the database and the web "
                    + "can be swapped without it noticing");

    @ArchTest
    static final ArchRule nothing_outside_an_adapter_depends_on_an_adapter = noClasses()
            .that()
            .resideOutsideOfPackages("..adapter..", "..config..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter..")
            .because("adapters are the outermost ring; config is the one place allowed to wire them");

    @ArchTest
    static final ArchRule ports_are_owned_by_the_application_layer = classes()
            .that()
            .haveSimpleNameEndingWith("Repository")
            .and()
            .areInterfaces()
            .should()
            .resideInAPackage("..application.port.out..")
            .because("the port is declared by the layer that needs it, not by the adapter that "
                    + "happens to implement it - that inversion is what makes the domain testable");

    @ArchTest
    static final ArchRule analytics_cannot_write = noClasses()
            .that()
            .resideInAPackage("..analytics..")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("WriteRepository")
            .because("the dashboard reports on pay; it must have no way of changing it");

    // Rule 4 of docs/03-ARCHITECTURE.md, which was documented but unenforced until a band needed
    // JobTitle: the allowlist rule above permits any ..domain.. to depend on any other ..domain..,
    // so one module reaching into another's internals passed silently. A type two modules both
    // need belongs in shared; anything else they need from each other goes through a port.
    @ArchTest
    static final ArchRule modules_do_not_reach_into_each_others_internals = SlicesRuleDefinition.slices()
            .matching("com.acme.salarymanagement.(*)..")
            .namingSlices("module $1")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(
                    DescribedPredicate.alwaysTrue(),
                    JavaClass.Predicates.resideInAnyPackage("com.acme.salarymanagement.shared..", "java..", "javax.."))
            .because("a module's domain and persistence are its own; what two modules share belongs "
                    + "in the kernel, and what one needs from another goes through its inbound port");

    @ArchTest
    static final ArchRule modules_are_free_of_cycles = slices().matching("com.acme.salarymanagement.(*)..")
            .should()
            .beFreeOfCycles()
            .because("a cycle between modules means they are one module that has not admitted it");

    // Structural, not a property of one field: pay changes through changeSalaryTo, which returns
    // the revision recording it. A setter anywhere in the domain is a way to move state without
    // producing the evidence, so the rule is written for every domain class rather than for
    // Employee.currentSalary alone.
    @ArchTest
    static final ArchRule the_domain_has_no_setters = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("..domain..")
            .should()
            .haveNameMatching("set[A-Z].*")
            .because("a setter is a way to change state without producing the record of the change; "
                    + "pay moves through changeSalaryTo, which returns the SalaryRevision that proves it");

    // The audit guarantee is that a revision can only come from changeSalaryTo. That holds while
    // the aggregate is the only thing that can make one: an adapter able to build a SalaryRevision
    // from an employee's before and after state would let any code path that changes pay produce
    // a plausible-looking record, and the log would go on looking trustworthy while no longer
    // being derived from the only method allowed to move pay.
    @ArchTest
    static final ArchRule only_the_domain_creates_a_salary_revision = noClasses()
            .that()
            .resideOutsideOfPackage("..domain..")
            .should()
            .callConstructorWhere(target(owner(type(SalaryRevision.class))))
            .because("a revision is what changeSalaryTo produces; anything that can mint one "
                    + "separately makes the audit log a record of what someone chose to write down");

    @ArchTest
    static final ArchRule dependencies_are_injected_through_constructors = fields().should()
            .notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("field injection hides a class's real dependencies and makes it "
                    + "impossible to construct in a test without a container");
}
