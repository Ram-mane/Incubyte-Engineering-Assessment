package com.acme.salarymanagement.employee.application.port.in;

import java.util.List;

import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;

/**
 * A page of the directory.
 *
 * @param employees the rows, in the directory's total order
 * @param nextCursor where to resume, or null when this is the last page
 * @param totalApprox how many match the filters; counted on the first page only, null thereafter
 */
public record EmployeePage(List<EmployeeSummary> employees, String nextCursor, Long totalApprox) {}
