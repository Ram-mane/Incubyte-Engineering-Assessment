package com.acme.salarymanagement.employee.adapter.in.web;

import java.util.List;

import com.acme.salarymanagement.employee.application.port.in.EmployeePage;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The documented collection shape: {@code {items, nextCursor, totalApprox}} (04-API-DESIGN.md).
 *
 * <p>No page number and no page count, deliberately. A keyset-paged collection has no page
 * numbers to give - it has a place to resume from - and publishing one would be inviting clients
 * to build the deep-linking that the pagination strategy cannot support.
 *
 * <p>{@code nextCursor} and {@code totalApprox} are omitted rather than sent as null: absent
 * means "no next page" and "not recounted on this page", and a client that checks for the field
 * reads both correctly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record EmployeePageResponse(List<EmployeeResponse> items, String nextCursor, Long totalApprox) {

    static EmployeePageResponse of(EmployeePage page) {
        return new EmployeePageResponse(
                page.employees().stream().map(EmployeeResponse::of).toList(), page.nextCursor(), page.totalApprox());
    }
}
