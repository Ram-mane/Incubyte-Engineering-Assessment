package com.acme.salarymanagement.employee.adapter.in.web;

import java.util.List;

import com.acme.salarymanagement.employee.application.port.in.EmployeePage;

record EmployeePageResponse(List<EmployeeResponse> items, int page, int size, long total) {

    static EmployeePageResponse of(EmployeePage page) {
        return new EmployeePageResponse(
                page.employees().stream().map(EmployeeResponse::of).toList(), page.page(), page.size(), page.total());
    }
}
