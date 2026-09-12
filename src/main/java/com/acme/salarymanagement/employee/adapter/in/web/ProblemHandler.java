package com.acme.salarymanagement.employee.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.acme.salarymanagement.employee.application.service.EmployeeNotFound;
import com.acme.salarymanagement.employee.domain.ConcurrentSalaryChange;

/**
 * Turns what the caller got wrong into what the caller gets back.
 *
 * <p>Deliberately narrow for now. A malformed cursor or an impossible country code is a 400; an
 * employee who is not here is a 404; a change the domain refuses because the employee is
 * TERMINATED is a 422. The finer mapping 04-API-DESIGN describes - 422 for a non-positive amount
 * or a currency that does not match the country, 409 for a no-op - needs the domain to distinguish
 * those rejections by type rather than by message, and arrives with the RFC 7807 work at 2.8.
 * Until then they are 400s: wrong in the code, right in the refusal.
 */
@RestControllerAdvice
class ProblemHandler {

    @ExceptionHandler({EmployeeNotFound.class})
    ProblemDetail thereIsNoSuchEmployee(EmployeeNotFound missing) {
        return problem(HttpStatus.NOT_FOUND, "No such employee", missing.getMessage());
    }

    @ExceptionHandler({ConcurrentSalaryChange.class})
    ProblemDetail somebodyElseChangedItFirst(ConcurrentSalaryChange lost) {
        // 409, not 422: nothing about the request was wrong, and repeating it unchanged is exactly
        // what the caller must not do. 04-API-DESIGN reserves 409 for this.
        return problem(HttpStatus.CONFLICT, "This pay change was not applied", lost.getMessage());
    }

    @ExceptionHandler({IllegalStateException.class})
    ProblemDetail theDomainRefusedTheChange(IllegalStateException refused) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "The change was refused", refused.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class})
    ProblemDetail theRequestAskedForSomethingImpossible(IllegalArgumentException refused) {
        return problem(HttpStatus.BAD_REQUEST, "The request could not be understood", refused.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        // Domain messages name the employee and the currencies but never an amount (D080), so
        // this cannot leak pay. Nothing here echoes a stack trace or any SQL.
        problem.setDetail(detail);
        return problem;
    }
}
