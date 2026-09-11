package com.acme.salarymanagement.employee.application.port.out;

import java.util.List;

import com.acme.salarymanagement.employee.application.port.in.DirectoryCursor;
import com.acme.salarymanagement.employee.application.port.in.DirectoryFilters;

/** Reading the directory. Declared by the layer that needs it, implemented by persistence. */
public interface EmployeeDirectoryRepository {

    /**
     * The matching employees after the cursor, in the directory's total order.
     *
     * @param limit how many to return; the caller asks for one more than it needs, to learn
     *     whether another page exists without counting
     */
    List<EmployeeSummary> findPage(DirectoryFilters filters, String search, DirectoryCursor after, int limit);

    long count(DirectoryFilters filters, String search);

    /** The distinct values of one filterable column, in the order a dropdown should show them. */
    List<String> distinctValuesOf(FilterableColumn column);
}
