package com.acme.salarymanagement.employee.adapter.in.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.acme.salarymanagement.employee.domain.EmailAddress;
import com.acme.salarymanagement.employee.domain.Employee;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.employee.domain.EmployeeNumber;
import com.acme.salarymanagement.employee.domain.EmploymentStatus;
import com.acme.salarymanagement.employee.domain.PersonName;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * A plausible org, generated from a fixed seed.
 *
 * <p>Deterministic on purpose: the demo, the tests and the load test must see identical data, so
 * a number quoted in one is the number in the others. Everything derives from one {@link Random}
 * seeded with a constant, and nothing here reads a clock - the hire dates are computed from a
 * fixed reference date, so the same seed produces the same org next year.
 *
 * <p>Salaries are drawn per country in that country's own currency, never converted. The spread
 * across markets is the point: a dashboard that normalises six currencies has nothing to prove
 * against ten thousand people paid in one.
 */
final class EmployeePopulation {

    private static final long FIXED_SEED = 20_260_911L;
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 9, 1);
    private static final int EARLIEST_HIRE_YEARS_AGO = 11;

    private static final List<String> GIVEN_NAMES = List.of(
            "Asha", "Ravi", "Meera", "Arjun", "Priya", "Karan", "Lena", "Tobias", "Sofia", "Jonas", "Emma", "Oliver",
            "Grace", "Henry", "Wei", "Mei", "Siti", "Daniel", "Chloe", "Noah", "Ines", "Marta", "Felix", "Amara",
            "Ibrahim", "Yuki", "Hana", "Liam", "Zara", "Omar");

    private static final List<String> FAMILY_NAMES = List.of(
            "Rao",
            "Kapoor",
            "Iyer",
            "Mehta",
            "Nair",
            "Fischer",
            "Weber",
            "Schmidt",
            "Becker",
            "Hughes",
            "Clarke",
            "Whitfield",
            "Osborne",
            "Tan",
            "Lim",
            "Ng",
            "Chen",
            "Nguyen",
            "Mitchell",
            "Sullivan",
            "Okafor",
            "Adeyemi",
            "Silva",
            "Moreau",
            "Kowalski",
            "Novak");

    private static final List<Department> DEPARTMENTS = List.of(
                    "Engineering", "Product", "Design", "Sales", "Marketing", "Finance", "People", "Customer Success")
            .stream()
            .map(Department::new)
            .toList();

    private static final Map<Department, List<String>> TITLES_BY_DEPARTMENT = Map.of(
            DEPARTMENTS.get(0), List.of("Software Engineer", "Site Reliability Engineer", "Data Engineer"),
            DEPARTMENTS.get(1), List.of("Product Manager", "Technical Program Manager"),
            DEPARTMENTS.get(2), List.of("Product Designer", "UX Researcher"),
            DEPARTMENTS.get(3), List.of("Account Executive", "Sales Engineer"),
            DEPARTMENTS.get(4), List.of("Marketing Manager", "Content Strategist"),
            DEPARTMENTS.get(5), List.of("Financial Analyst", "Accountant"),
            DEPARTMENTS.get(6), List.of("People Partner", "Recruiter"),
            DEPARTMENTS.get(7), List.of("Customer Success Manager", "Support Engineer"));

    /** Annual base by level, in each country's own currency. Six markets, six pay scales. */
    private static final Map<String, List<String>> PAY_SCALE = Map.of(
            "IN", List.of("800000", "1400000", "2200000", "3200000", "4500000"),
            "US", List.of("75000", "110000", "150000", "190000", "240000"),
            "DE", List.of("55000", "75000", "95000", "120000", "150000"),
            "GB", List.of("48000", "65000", "85000", "105000", "135000"),
            "SG", List.of("70000", "95000", "125000", "155000", "190000"),
            "AU", List.of("80000", "105000", "135000", "165000", "200000"));

    private static final List<CountryCode> COUNTRIES = List.of("IN", "US", "DE", "GB", "SG", "AU").stream()
            .map(CountryCode::new)
            .toList();

    private EmployeePopulation() {}

    static List<Employee> of(int size) {
        Random random = new Random(FIXED_SEED);
        List<Employee> employees = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            employees.add(anEmployee(index, random));
        }
        return employees;
    }

    private static Employee anEmployee(int index, Random random) {
        CountryCode country = COUNTRIES.get(random.nextInt(COUNTRIES.size()));
        Department department = DEPARTMENTS.get(random.nextInt(DEPARTMENTS.size()));
        List<String> titles = TITLES_BY_DEPARTMENT.get(department);
        SeniorityLevel level = SeniorityLevel.values()[random.nextInt(SeniorityLevel.values().length)];
        String given = GIVEN_NAMES.get(random.nextInt(GIVEN_NAMES.size()));
        String family = FAMILY_NAMES.get(random.nextInt(FAMILY_NAMES.size()));

        return new Employee(
                new EmployeeId(new UUID(FIXED_SEED, index)),
                new EmployeeNumber("E%05d".formatted(index + 1)),
                new PersonName(given, family),
                // The index keeps it unique: there are thirty given names and twenty-six family
                // names, and ten thousand people cannot all have distinct ones.
                new EmailAddress("%s.%s.%d@acme.example".formatted(lower(given), lower(family), index + 1)),
                country,
                department,
                new JobTitle(titles.get(random.nextInt(titles.size()))),
                level,
                hireDate(random),
                EmploymentStatus.ACTIVE,
                salary(country, level, random));
    }

    private static Money salary(CountryCode country, SeniorityLevel level, Random random) {
        BigDecimal base = new BigDecimal(PAY_SCALE.get(country.code()).get(level.ordinal()));
        // Plus or minus twelve percent, so a band has a spread to sit people inside. Drawn as an
        // integer count of percentage points and scaled, never as a double: a float anywhere near
        // a salary is the thing this codebase refuses, and "it is only test data" is how it gets
        // in - the seed's numbers are the numbers the dashboard totals.
        BigDecimal spread = BigDecimal.valueOf(88L + random.nextInt(25), 2);
        return Money.of(base.multiply(spread).setScale(2, RoundingMode.HALF_EVEN), country.currency());
    }

    private static LocalDate hireDate(Random random) {
        return REFERENCE_DATE.minusDays(random.nextInt(EARLIEST_HIRE_YEARS_AGO * 365));
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
