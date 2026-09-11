package com.acme.salarymanagement.employee.application.port.in;

import java.util.List;

import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;

/** A page of the directory, and enough context for a pager to render itself. */
public record EmployeePage(List<EmployeeSummary> employees, int page, int size, long total) {}
