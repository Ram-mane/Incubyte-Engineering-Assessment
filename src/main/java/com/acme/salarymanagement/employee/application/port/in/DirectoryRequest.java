package com.acme.salarymanagement.employee.application.port.in;

/**
 * One page of the directory as the screen asks for it.
 *
 * @param search free text matched against name and email; null for everyone
 * @param filters the four named dimensions
 * @param after where the previous page stopped; null for the first page
 * @param limit how many people to return
 */
public record DirectoryRequest(String search, DirectoryFilters filters, DirectoryCursor after, int limit) {

    public boolean isFirstPage() {
        return after == null;
    }
}
