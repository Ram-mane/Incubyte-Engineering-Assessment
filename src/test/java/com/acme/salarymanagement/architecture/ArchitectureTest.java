package com.acme.salarymanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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

    @ArchTest
    static final ArchRule modules_are_free_of_cycles = slices().matching("com.acme.salarymanagement.(*)..")
            .should()
            .beFreeOfCycles()
            .because("a cycle between modules means they are one module that has not admitted it");

    @ArchTest
    static final ArchRule dependencies_are_injected_through_constructors = fields().should()
            .notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("field injection hides a class's real dependencies and makes it "
                    + "impossible to construct in a test without a container");
}
