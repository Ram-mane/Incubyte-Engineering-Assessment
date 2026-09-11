package com.acme.salarymanagement.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The department is stored on every employee row and grouped by in every breakdown, so its value
 * has to be the same string each time. Equality that depends on stray whitespace would split one
 * department into two on the dashboard.
 */
class DepartmentTest {

    @Test
    void a_department_name_is_trimmed_so_two_spellings_do_not_become_two_departments() {
        assertThat(new Department("  Engineering ")).isEqualTo(new Department("Engineering"));
    }

    @Test
    void a_blank_department_names_no_department_and_is_rejected() {
        assertThatThrownBy(() -> new Department("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("department");
    }

    @Test
    void no_department_at_all_is_rejected() {
        assertThatThrownBy(() -> new Department(null)).isInstanceOf(NullPointerException.class);
    }
}
