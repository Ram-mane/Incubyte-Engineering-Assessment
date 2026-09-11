package com.acme.salarymanagement.employee.application.port.out;

import java.util.List;

/** Reading the directory. Declared by the layer that needs it, implemented by persistence. */
public interface EmployeeDirectoryRepository {

    List<EmployeeSummary> findPage(long offset, int limit);

    long count();
}
