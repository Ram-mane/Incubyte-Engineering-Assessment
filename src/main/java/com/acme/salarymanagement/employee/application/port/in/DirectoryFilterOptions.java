package com.acme.salarymanagement.employee.application.port.in;

import java.util.List;

import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * What there is to filter by, read from the people who are actually here.
 *
 * <p>Derived rather than configured: the directory offers the departments that exist, so a
 * department nobody works in cannot be chosen and a new one appears without a deployment. Levels
 * come from the enum instead, because that list is the domain's and not the data's.
 */
public record DirectoryFilterOptions(
        List<String> countries, List<String> departments, List<String> jobTitles, List<SeniorityLevel> levels) {}
